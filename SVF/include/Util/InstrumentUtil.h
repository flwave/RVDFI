#ifndef InstrumentUtil_H_
#define InstrumentUtil_H_

#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/IR/Function.h>

namespace instrumentUtil {

/// This function is the prototype of 'printf' in c
llvm::FunctionType *printfPrototype(llvm::LLVMContext &ctx, llvm::Module *module);

/// This function declares 'printf' in module
llvm::Constant *printfDecl(llvm::LLVMContext &ctx, llvm::Module *module);

}

#endif /* InstrumentUtil_H_ */
