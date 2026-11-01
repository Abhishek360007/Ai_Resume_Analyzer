# LLM Studio Setup Guide

## Recommended Models for Resume Analyzer

### Primary LLM (for Analysis and Matching)
**Recommended: Ministral 3 14B Reasoning** (`mistralai/ministral-3-14b-reasoning`)

#### Why This Model?
- **Excellent instruction following**: Critical for structured JSON output
- **Reasoning-optimized**: Strong at multi-step resume analysis and candidate matching
- **Native JSON generation**: Best-in-class agentic/structured output capabilities
- **Efficient**: Runs on ~10 GB minimum system memory (14B params)

#### Alternative Models (in order of preference):
1. **Ministral 3 14B** (non-reasoning) - Faster for latency-sensitive tasks
2. **LLaMA 3.1 8B Instruct** - Lighter, still very capable
3. **Mistral 7B Instruct v0.3** - Proven reliability on lower-end hardware
4. **Qwen 2.5 14B Instruct** - Excellent but larger (needs 24GB+ RAM)
5. **Mixtral 8x7B Instruct** - Very powerful but resource-intensive (32GB+ RAM)

### Embedding Model
**Recommended: nomic-embed-text v1.5**

#### Why This Model?
- **Purpose-built for embeddings**: Optimized for semantic search
- **768-dimensional vectors**: Good balance of quality and efficiency  
- **Local-first**: Designed to run entirely on your machine
- **Excellent for RAG**: Retrieval-Augmented Generation use cases

#### Alternative Embedding Models:
1. **all-MiniLM-L6-v2** - Faster, smaller, still effective
2. **bge-large-en-v1.5** - Larger, more accurate

## Setup Instructions

### 1. Install LLM Studio
Download from: [LM Studio Official Website](https://lmstudio.ai/)

### 2. Load the Recommended Models

#### Load Main LLM:
1. Open LM Studio
2. Go to "Search" or "Discover"
3. Search for: **"ministral-3-14b-reasoning"** (or **"llama-3.1-8b-instruct"**) in LM Studio's Discover tab
4. Download the GGUF quantized version:
   - For 16GB RAM: `Q4_K_M` quantization (4-bit)
   - For 32GB RAM: `Q5_K_M` or `Q6_K` quantization (5-6 bit)

#### Load Embedding Model:
1. In LM Studio, search for: **"nomic-embed-text"**
2. Download the model (smaller than main LLM)

### 3. Start the Local Server

1. In LM Studio, go to "Local Server" tab
2. Select your downloaded main model (Mistral or LLaMA)
3. Configure settings:
   ```
   Context Length: 4096 or higher
   Temperature: 0.7 (default)
   Max Tokens: 2000
   ```
4. Click "Start Server"
5. Server should start on: `http://localhost:1234`

### 4. Verify Server is Running

Test with curl:
```powershell
curl http://localhost:1234/v1/models
```

Expected response should list your loaded model.

### 5. (Optional) Enable an API Key

1. In LM Studio go to the **Developer** tab
2. Under **API Key**, click **Generate** (or enable an existing key)
3. Copy the key and set it as `LLM_STUDIO_API_KEY` in your `.env`

The application authenticates with `Authorization: Bearer <api-key>` on every request — the exact auth scheme LM Studio's local server expects. If no key is configured the app falls back to `not-needed` (LM Studio accepts unauthenticated requests when no key is enabled). When a key *is* enabled in LM Studio, requests without it are rejected with `401`, which the health check reports as "LM Studio authentication failed (check LLM_STUDIO_API_KEY)".

### 6. Load Embedding Model (if separate server needed)

Some setups may require running embeddings on a separate port or instance. Check LM Studio documentation for your version.

## Configuration in Application

The application is already configured to use:
- **Base URL**: `http://localhost:1234` (Spring AI appends `/v1` itself, so do **not** include `/v1` — otherwise requests hit `/v1/v1/...`)
- **Model**: `mistralai/ministral-3-14b-reasoning` (configurable in `application.yml`)
- **Embedding Model**: `text-embedding-nomic-embed-text-v1.5`
- **API Key**: `LLM_STUDIO_API_KEY` (sent as `Authorization: Bearer <token>`; defaults to `not-needed`)

### Changing Models

Edit `src/main/resources/application.yml` (the OpenAI-compatible `spring.ai.openai` block):

```yaml
spring:
  ai:
    openai:
      base-url: ${LLM_STUDIO_BASE_URL}
      api-key: ${LLM_STUDIO_API_KEY:not-needed}
      chat:
        options:
          model: ${LLM_STUDIO_MODEL}
      embedding:
        options:
          model: ${LLM_STUDIO_EMBEDDING_MODEL}
```

## Performance Tips

### Hardware Requirements
- **Minimum**: 16GB RAM, 4-core CPU, 50GB disk space
- **Recommended**: 32GB RAM, 8-core CPU, SSD with 100GB free
- **Optimal**: 64GB RAM, 16-core CPU OR dedicated GPU with 12GB+ VRAM

### Model Selection Based on Hardware
| RAM Available | Recommended Model | Quantization |
|---------------|-------------------|--------------|
| 16 GB | Ministral 3 14B Reasoning | Q4_K_M |
| 16 GB | Mistral 7B Instruct | Q4_K_M |
| 24 GB | LLaMA 3.1 8B Instruct | Q5_K_M |
| 32 GB | Qwen 2.5 14B Instruct | Q4_K_M |
| 48+ GB | Mixtral 8x7B Instruct | Q5_K_M |

### Optimization Settings in LLM Studio
- **Enable GPU acceleration** if you have a compatible NVIDIA GPU
- **Batch Size**: Set to 1 for resume processing (sequential works better)
- **Context Length**: 4096 is sufficient for most resumes
- **Thread Count**: Set to number of CPU cores - 2 (leave some for OS)

## Troubleshooting

### Server Won't Start
- Check if port 1234 is already in use
- Try restarting LM Studio
- Check system resources (RAM/CPU)

### Slow Response Times
- Reduce model size (use smaller quantization)
- Reduce context length
- Close other applications
- Enable GPU acceleration if available

### Poor Quality Results
- Try a larger model or higher quantization
- Increase temperature for more creative responses (0.8-0.9)
- Decrease temperature for more deterministic results (0.3-0.5)
- Check if model is fully loaded (watch LM Studio console)

### JSON Parsing Errors
- Mistral and LLaMA models are good at JSON
- If issues persist, try adjusting the prompt in `AIService.java`
- Consider adding system message emphasizing JSON-only output

## Model Performance Comparison for Resume Analysis

Based on community benchmarks and our use case:

| Model | Speed | Accuracy | JSON Quality | Resource Usage |
|-------|-------|----------|--------------|----------------|
| Ministral 3 14B Reasoning | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Medium |
| Mistral 7B Instruct v0.3 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Low |
| LLaMA 3.1 8B Instruct | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Low-Medium |
| Qwen 2.5 14B Instruct | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Medium-High |
| Mixtral 8x7B Instruct | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | High |

**Recommendation**: Start with **Ministral 3 14B Reasoning** for best accuracy and native JSON output. It is a reasoning model, so first-generation responses take slightly longer (it thinks before answering) and the app's prompts/max-tokens are sized to accommodate that.

### Reasoning Model Notes

Ministral 3 14B Reasoning is a *reasoning* model — LM Studio emits a `reasoning_content` field (thinking trace) before the final `content`. A few things to know:

- **Empty `content`**: if the model spends its whole token budget on reasoning, `content` can come back empty. The app treats that as "LLM returned an empty response" and falls back. If you see this, increase `max-tokens` in `application.yml` (currently 4000 default) or lower the model's reasoning effort in LM Studio.
- **Latency**: reasoning tokens add to generation time. Budget accordingly on the match/analysis endpoints.
- **JSON reliability**: the model's native JSON output makes it well suited to the app's structured prompts.
