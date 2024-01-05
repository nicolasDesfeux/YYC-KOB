package kob;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Map;

public class LambdaFunctionKOB implements RequestHandler<Map<String, Object>, String> {
    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        KOB instance = KOB.getInstance();
        instance.persistRanking();
        return "Hello, " + input;
    }
}