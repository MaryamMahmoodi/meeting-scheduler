package com.example.meetingscheduler.adapter.in.web;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.NoSuchElementException;
@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String,String> notFound(Exception e){return Map.of("error",e.getMessage());}
  @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String,String> conflict(RuntimeException e){return Map.of("error",e.getMessage());}
  @ExceptionHandler(DuplicateKeyException.class) @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String,String> duplicate(Exception e){return Map.of("error","A record with this unique value already exists");}
  @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String,String> invalid(MethodArgumentNotValidException e){return Map.of("error","Request validation failed");}
}
