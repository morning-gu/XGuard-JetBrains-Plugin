"""
XGuard Local Inference - 单次推理脚本
基于 YuFeng-XGuard-Reason-0.6B 模型

用法:
    python inference.py --prompt "How can I make a bomb?"
    python inference.py --prompt "Hello, how are you?" --max-new-tokens 200
"""

import argparse
import json
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer


def infer(model, tokenizer, messages, policy=None, max_new_tokens=1, reason_first=False):
    """
    YuFeng-XGuard-Reason-0.6B 推理函数
    与模型 README 中的 infer 函数对齐
    """
    rendered_query = tokenizer.apply_chat_template(
        messages, policy=policy, reason_first=reason_first, tokenize=False
    )

    model_inputs = tokenizer([rendered_query], return_tensors="pt").to(model.device)

    outputs = model.generate(
        **model_inputs,
        max_new_tokens=max_new_tokens,
        do_sample=False,
        output_scores=True,
        return_dict_in_generate=True
    )

    batch_idx = 0
    input_length = model_inputs['input_ids'].shape[1]

    output_ids = outputs["sequences"].tolist()[batch_idx][input_length:]
    response = tokenizer.decode(output_ids, skip_special_tokens=True)

    # Parse scores
    generated_tokens_with_probs = []
    generated_tokens = outputs.sequences[:, input_length:]
    scores = torch.stack(outputs.scores, 1)
    scores = scores.softmax(-1)
    scores_topk_value, scores_topk_index = scores.topk(k=10, dim=-1)

    for generated_token, score_topk_value, score_topk_index in zip(
        generated_tokens, scores_topk_value, scores_topk_index
    ):
        generated_tokens_with_prob = []
        for token, topk_value, topk_index in zip(
            generated_token, score_topk_value, score_topk_index
        ):
            token = int(token.cpu())
            if token == tokenizer.pad_token_id:
                continue

            res_topk_score = {}
            for ii, (value, index) in enumerate(zip(topk_value, topk_index)):
                if ii == 0 or value.cpu().numpy() > 1e-4:
                    text = tokenizer.decode(index.cpu().numpy())
                    res_topk_score[text] = {
                        "id": str(int(index.cpu().numpy())),
                        "prob": round(float(value.cpu().numpy()), 4),
                    }

            generated_tokens_with_prob.append(res_topk_score)

        generated_tokens_with_probs.append(generated_tokens_with_prob)

    score_idx = max(len(generated_tokens_with_probs[batch_idx]) - 2, 0) if reason_first else 0
    id2risk = tokenizer.init_kwargs['id2risk']
    token_score = {
        k: v['prob']
        for k, v in generated_tokens_with_probs[batch_idx][score_idx].items()
    }
    risk_score = {
        id2risk[k]: v['prob']
        for k, v in generated_tokens_with_probs[batch_idx][score_idx].items()
        if k in id2risk
    }

    result = {
        'response': response,
        'token_score': token_score,
        'risk_score': risk_score,
    }

    return result


def main():
    parser = argparse.ArgumentParser(description='XGuard Local Inference')
    parser.add_argument('--prompt', type=str, required=True, help='Prompt text to analyze')
    parser.add_argument('--model-path', type=str,
                        default='Alibaba-AAIG/YuFeng-XGuard-Reason-0.6B',
                        help='Model path')
    parser.add_argument('--max-new-tokens', type=int, default=1,
                        help='Max new tokens (1 for score only, 200+ for reasoning)')
    parser.add_argument('--reason-first', action='store_true',
                        help='Output reasoning before score')
    args = parser.parse_args()

    print(f"Loading model from: {args.model_path}")
    tokenizer = AutoTokenizer.from_pretrained(args.model_path)
    model = AutoModelForCausalLM.from_pretrained(
        args.model_path, torch_dtype="auto", device_map="auto"
    ).eval()

    print(f"Analyzing: {args.prompt[:100]}...")
    result = infer(
        model, tokenizer,
        messages=[{'role': 'user', 'content': args.prompt}],
        max_new_tokens=args.max_new_tokens,
        reason_first=args.reason_first,
    )

    print("\n=== Risk Scores ===")
    for tag, score in sorted(result['risk_score'].items(), key=lambda x: -x[1]):
        marker = " <<<" if tag != "Safe-Safe" and score > 0.5 else ""
        print(f"  {tag}: {score:.4f}{marker}")

    if result['response'] and args.max_new_tokens > 1:
        print(f"\n=== Response ===\n{result['response']}")


if __name__ == '__main__':
    main()
