package com.psw.cta;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;

/**
 * Main class to be used in AWS lambda.
 */
public class ServiceHandler implements RequestHandler<Input, Object> {

  @Override
  public Object handleRequest(Input input, Context context) {


    return "Lambda Function is invoked....";
  }


}
