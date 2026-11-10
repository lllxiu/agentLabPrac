package org.example.backend_practice.web;

import org.example.backend_practice.exception.ConversationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.example.backend_practice.dto.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handlerValidation(MethodArgumentNotValidException ex){
        FieldError error =ex.getBindingResult().getFieldError();
        String message = "请求不合法";
        if(error!=null){
            message = error.getDefaultMessage();

        }
        return new ErrorResponse("INVALID_ARGUMENT", message);
    }



    //找不到会话返回404
    @ExceptionHandler(ConversationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleConversationNotFound(ConversationNotFoundException ex){
        return new ErrorResponse("CONVERSATION_NOT_FOUND", ex.getMessage());
    }
}
