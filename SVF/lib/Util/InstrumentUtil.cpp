/*
 * InstrumentUtil.cpp
 *
 * Created on: Apr 25, 2018
 *     Author: Jiayi Huang
 */

#include "Util/InstrumentUtil.h"

#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/IR/Function.h>

using namespace llvm;

FunctionType* instrumentUtil::printfPrototype(LLVMContext &ctx, Module *module) {

    Type *localInt = Type::getInt32Ty(ctx);
    Type *stringPtr = Type::getInt8PtrTy(ctx);
    auto funcArgs = std::vector<Type*>(1, stringPtr);
    FunctionType* printfType =
        FunctionType::get(localInt, ArrayRef<Type *>(funcArgs), true);

    return printfType;
}

Constant* instrumentUtil::printfDecl(LLVMContext &ctx, Module *module) {

    // get function type for declaration
    FunctionType *printfType = printfPrototype(ctx, module);

    // insert function declaration
    Constant *printfFunc = module->getOrInsertFunction("printf", printfType);

    return printfFunc;
}
