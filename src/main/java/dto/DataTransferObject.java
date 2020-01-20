package dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class DataTransferObject<T> {
    public abstract T save();

    public String toJSONString() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        // Java object to JSON string
        return mapper.writeValueAsString(this);
    }
}
