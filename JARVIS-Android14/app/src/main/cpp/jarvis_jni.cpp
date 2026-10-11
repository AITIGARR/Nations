#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include "llama.h"

static void fill(llama_batch_ext * b, const llama_token * t, int n, llama_pos begin) {
    llama_batch_ext_clear(b);
    for (int i=0;i<n;i++) {
        int j=llama_batch_ext_add_token(b,0,t[i]);
        llama_pos position=begin+i;
        llama_batch_ext_set_pos(b,j,&position);
    }
    llama_batch_ext_set_output_logits(b,n-1,true);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_horizon_jarvis_LocalBrain_generate(JNIEnv* env,jclass,jstring pathStr,jstring questionStr) {
    static std::mutex mutex;
    std::lock_guard<std::mutex> guard(mutex);
    const char * cp=env->GetStringUTFChars(pathStr,nullptr);
    const char * cq=env->GetStringUTFChars(questionStr,nullptr);
    std::string path(cp?cp:""),question(cq?cq:"");
    if(cp)env->ReleaseStringUTFChars(pathStr,cp);
    if(cq)env->ReleaseStringUTFChars(questionStr,cq);
    static std::once_flag once;
    std::call_once(once,[]{llama_backend_init();});
    // Keep the Qwen model in RAM after the first prompt. Loading a 400MB GGUF
    // for every question is much slower than the actual short inference.
    static llama_model *cached_model = nullptr;
    static std::string cached_path;
    if (!cached_model || cached_path != path) {
        if (cached_model) { llama_model_free(cached_model); cached_model = nullptr; }
        llama_model_params mp=llama_model_default_params();
        mp.n_gpu_layers=0;
        cached_model=llama_model_load_from_file(path.c_str(),mp);
        if (cached_model) cached_path=path;
    }
    llama_model* model=cached_model;
    if(!model)return env->NewStringUTF("GGUF could not be loaded.");
    const llama_vocab* vocab=llama_model_get_vocab(model);
    const std::string prompt=
        "<|im_start|>system\nYou are JARVIS, a concise helpful assistant. "
        "Reply in the user's language. Be accurate, brief, and helpful. "
        "Admit uncertainty.\n<|im_end|>\n"
        "<|im_start|>user\n"+question+"\n<|im_end|>\n<|im_start|>assistant\n";
    int n=llama_tokenize(vocab,prompt.c_str(),prompt.length(),nullptr,0,true,true);
    if(n<0)n=-n;
    if(n<1 || n>1000){return env->NewStringUTF("Prompt too long.");}
    std::vector<llama_token> tokens(n);
    if(llama_tokenize(vocab,prompt.c_str(),prompt.length(),tokens.data(),n,true,true)<0){
        return env->NewStringUTF("Tokenization failed.");
    }
    llama_context_params cpms=llama_context_default_params();
    cpms.n_ctx=1536;
    cpms.n_batch=std::max(512,n);
    cpms.n_threads=3;
    cpms.n_threads_batch=3;
    llama_context* ctx=llama_init_from_model(model,cpms);
    if(!ctx){return env->NewStringUTF("Cannot initialize local inference.");}
    llama_batch_ext* batch=llama_batch_ext_init(ctx);
    llama_sampler* sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler,llama_sampler_init_greedy());
    fill(batch,tokens.data(),n,0);
    llama_pos pos=n;
    std::string output;
    for(int i=0;i<128;i++){
        if(llama_process(ctx,LLAMA_PROCESS_TYPE_DECODE,batch)!=0)break;
        llama_token next=llama_sampler_sample(sampler,ctx,-1);
        if(llama_vocab_is_eog(vocab,next))break;
        char piece[512];
        int bytes=llama_token_to_piece(vocab,next,piece,sizeof(piece),0,true);
        if(bytes>0)output.append(piece,bytes);
        fill(batch,&next,1,pos++);
        if(output.length()>1800)break;
    }
    llama_sampler_free(sampler);
    llama_batch_ext_free(batch);
    llama_free(ctx);
    return env->NewStringUTF(output.c_str());
}
