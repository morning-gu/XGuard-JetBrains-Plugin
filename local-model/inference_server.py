"""
XGuard Local Inference Server
基于 YuFeng-XGuard-Reason-0.6B 模型的本地推理 HTTP 服务

启动方式:
    python inference_server.py --port 18797 --model-path ./models

API:
    GET  /health          - 健康检查
    POST /infer           - 执行风险检测推理
"""

import argparse
import json
import time
import traceback
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Dict, List, Optional, Any

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer


# ============================================================
# Model Manager
# ============================================================

class XGuardModelManager:
    """YuFeng-XGuard-Reason-0.6B 模型管理器"""

    def __init__(self, model_path: str = "Alibaba-AAIG/YuFeng-XGuard-Reason-0.6B"):
        self.model_path = model_path
        self.model = None
        self.tokenizer = None
        self.id2risk = None
        self._loaded = False

    def load(self):
        """加载模型和分词器"""
        if self._loaded:
            return

        print(f"[XGuard] Loading model from: {self.model_path}")
        start_time = time.time()

        self.tokenizer = AutoTokenizer.from_pretrained(self.model_path)

        # device_map="auto" 在纯 CPU + PyTorch 2.7+ 环境下会触发
        # tie_weights 中的 torch.equal(Meta tensor) 导致 NotImplementedError，
        # 因此在无 CUDA 时改用显式 device 指定
        if torch.cuda.is_available():
            self.model = AutoModelForCausalLM.from_pretrained(
                self.model_path,
                torch_dtype="auto",
                device_map="auto"
            ).eval()
        else:
            self.model = AutoModelForCausalLM.from_pretrained(
                self.model_path,
                torch_dtype="auto",
                device_map={"": "cpu"}
            ).eval()

        # 获取 id2risk 映射
        self.id2risk = self.tokenizer.init_kwargs.get('id2risk', {})

        elapsed = time.time() - start_time
        print(f"[XGuard] Model loaded in {elapsed:.2f}s")
        print(f"[XGuard] Risk categories: {len(self.id2risk)}")
        self._loaded = True

    def infer(
        self,
        messages: List[Dict[str, str]],
        policy: Optional[str] = None,
        max_new_tokens: int = 1,
        reason_first: bool = False,
    ) -> Dict[str, Any]:
        """
        执行推理

        Args:
            messages: 对话消息列表 [{"role": "user", "content": "..."}]
            policy: 动态策略文本（仅 8B 版本支持）
            max_new_tokens: 最大生成 token 数
            reason_first: 是否先输出归因

        Returns:
            推理结果字典
        """
        if not self._loaded:
            raise RuntimeError("Model not loaded")

        start_time = time.time()

        # 渲染 chat template
        rendered_query = self.tokenizer.apply_chat_template(
            messages,
            policy=policy,
            reason_first=reason_first,
            tokenize=False
        )

        model_inputs = self.tokenizer(
            [rendered_query],
            return_tensors="pt"
        ).to(self.model.device)

        # 生成
        with torch.no_grad():
            outputs = self.model.generate(
                **model_inputs,
                max_new_tokens=max_new_tokens,
                do_sample=False,
                output_scores=True,
                return_dict_in_generate=True
            )

        batch_idx = 0
        input_length = model_inputs['input_ids'].shape[1]

        # 解码响应文本
        output_ids = outputs["sequences"].tolist()[batch_idx][input_length:]
        response = self.tokenizer.decode(output_ids, skip_special_tokens=True)

        # 解析各风险维度的分值
        generated_tokens = outputs.sequences[:, input_length:]
        scores = torch.stack(outputs.scores, 1)
        scores = scores.softmax(-1)
        scores_topk_value, scores_topk_index = scores.topk(k=10, dim=-1)

        generated_tokens_with_probs = []
        for generated_token, score_topk_value, score_topk_index in zip(
            generated_tokens, scores_topk_value, scores_topk_index
        ):
            generated_tokens_with_prob = []
            for token, topk_value, topk_index in zip(
                generated_token, score_topk_value, score_topk_index
            ):
                token = int(token.cpu())
                if token == self.tokenizer.pad_token_id:
                    continue

                res_topk_score = {}
                for ii, (value, index) in enumerate(zip(topk_value, topk_index)):
                    if ii == 0 or value.cpu().numpy() > 1e-4:
                        text = self.tokenizer.decode(index.cpu().numpy())
                        res_topk_score[text] = {
                            "id": str(int(index.cpu().numpy())),
                            "prob": round(float(value.cpu().numpy()), 4),
                        }

                generated_tokens_with_prob.append(res_topk_score)

            generated_tokens_with_probs.append(generated_tokens_with_prob)

        # 提取风险分值
        score_idx = max(len(generated_tokens_with_probs[batch_idx]) - 2, 0) if reason_first else 0
        token_score = {
            k: v['prob']
            for k, v in generated_tokens_with_probs[batch_idx][score_idx].items()
        }
        risk_score = {
            self.id2risk[k]: v['prob']
            for k, v in generated_tokens_with_probs[batch_idx][score_idx].items()
            if k in self.id2risk
        }

        elapsed = time.time() - start_time

        # 提取归因文本
        explanation = ""
        if max_new_tokens > 1 and response:
            import re
            match = re.search(r'<explanation>(.*?)</explanation>', response, re.DOTALL)
            if match:
                explanation = match.group(1).strip()

        # 确定最高风险标签
        top_risk_tag = "Safe-Safe"
        top_risk_score = 0.0
        for tag, score in risk_score.items():
            if tag != "Safe-Safe" and score > top_risk_score:
                top_risk_score = score
                top_risk_tag = tag
        if top_risk_score == 0.0:
            top_risk_score = risk_score.get("Safe-Safe", 0.0)

        return {
            'risk_score': top_risk_score,
            'risk_tag': top_risk_tag,
            'explanation': explanation,
            'response': response,
            'token_score': token_score,
            'risk_scores': risk_score,
            'time': round(elapsed, 4)
        }


# ============================================================
# HTTP Server
# ============================================================

model_manager: Optional[XGuardModelManager] = None


class XGuardHandler(BaseHTTPRequestHandler):
    """HTTP 请求处理器"""

    def log_message(self, format, *args):
        """自定义日志格式"""
        print(f"[XGuard] {args[0]}")

    def _send_json_response(self, data: dict, status: int = 200):
        try:
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(data, ensure_ascii=False).encode('utf-8'))
        except (ConnectionAbortedError, ConnectionResetError, BrokenPipeError) as e:
            # 客户端已断开连接（超时/主动关闭），无法发送响应，仅记录日志
            print(f"[XGuard] Client disconnected before response could be sent: {e}")

    def _read_request_body(self) -> bytes:
        content_length = int(self.headers.get('Content-Length', 0))
        return self.rfile.read(content_length)

    def do_GET(self):
        if self.path == '/health':
            self._send_json_response({
                'status': 'ok',
                'model_loaded': model_manager is not None and model_manager._loaded
            })
        else:
            self._send_json_response({'error': 'Not found'}, 404)

    def do_POST(self):
        if self.path == '/infer':
            self._handle_infer()
        else:
            self._send_json_response({'error': 'Not found'}, 404)

    def _handle_infer(self):
        try:
            body = self._read_request_body()
            params = json.loads(body.decode('utf-8'))

            messages = params.get('messages', [])
            policy = params.get('policy', None)
            max_new_tokens = params.get('max_new_tokens', 1)
            enable_reasoning = params.get('enable_reasoning', True)
            reason_first = params.get('reason_first', False)

            print(f"[XGuard] >>> POST /infer | messages={len(messages)} | max_new_tokens={max_new_tokens} | enable_reasoning={enable_reasoning} | reason_first={reason_first}")
            print(f"[XGuard] >>> Request body: {json.dumps(params, ensure_ascii=False)}")

            if not messages:
                self._send_json_response({'error': 'messages is required'}, 400)
                return

            # 如果启用归因且 max_new_tokens 较小，自动增大
            if enable_reasoning and max_new_tokens <= 1:
                max_new_tokens = 200

            result = model_manager.infer(
                messages=messages,
                policy=policy,
                max_new_tokens=max_new_tokens,
                reason_first=reason_first,
            )

            print(f"[XGuard] <<< Response: {json.dumps(result, ensure_ascii=False)}")
            self._send_json_response(result)

        except Exception as e:
            traceback.print_exc()
            self._send_json_response({
                'error': str(e),
                'traceback': traceback.format_exc()
            }, 500)


# ============================================================
# Main
# ============================================================

def main():
    parser = argparse.ArgumentParser(description='XGuard Local Inference Server')
    parser.add_argument('--port', type=int, default=18797, help='HTTP server port')
    parser.add_argument('--model-path', type=str,
                        default='Alibaba-AAIG/YuFeng-XGuard-Reason-0.6B',
                        help='Model path (HuggingFace model ID or local path)')
    parser.add_argument('--host', type=str, default='127.0.0.1', help='HTTP server host')
    args = parser.parse_args()

    global model_manager

    # 加载模型
    model_manager = XGuardModelManager(model_path=args.model_path)
    model_manager.load()

    # 启动 HTTP 服务
    server = HTTPServer((args.host, args.port), XGuardHandler)
    print(f"[XGuard] Server started at http://{args.host}:{args.port}")
    print(f"[XGuard] Endpoints:")
    print(f"  GET  /health  - Health check")
    print(f"  POST /infer   - Risk inference")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[XGuard] Server shutting down...")
        server.server_close()


if __name__ == '__main__':
    main()
