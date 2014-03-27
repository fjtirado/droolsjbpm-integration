package org.kie.services.client.serialization;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.kie.services.client.serialization.jaxb.json.JaxbJacksonObjectMapper;

public class JsonSerializationProvider implements SerializationProvider {

    public final static int JMS_SERIALIZATION_TYPE = 1;

    private ObjectMapper mapper = new JaxbJacksonObjectMapper();
    private Class<?> outputType = null;

    public JsonSerializationProvider() {
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    }

    public String serialize(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonGenerationException jge) {
            throw new SerializationException("Unable to serialize " + object.getClass().getSimpleName() + " instance", jge);
        } catch (JsonMappingException jme) {
            throw new SerializationException("Unable to serialize " + object.getClass().getSimpleName() + " instance", jme);
        } catch (IOException ie) {
            throw new SerializationException("Unable to serialize " + object.getClass().getSimpleName() + " instance", ie);
        }
    }

    public void setDeserializeOutputClass(Class<?> type) {
        this.outputType = type;
    }

    public Object deserialize(Object jsonStrObject) {
        if( ! (jsonStrObject instanceof String) ) { 
            throw new UnsupportedOperationException(JaxbSerializationProvider.class.getSimpleName() + " can only deserialize Strings");
        }
        String jsonStr = (String) jsonStrObject;
        
        try {
            return mapper.readValue(jsonStr, this.outputType);
        } catch (JsonGenerationException jge) {
            throw new SerializationException("Unable to deserialize String " + outputType.getClass().getSimpleName() + " instance", jge);
        } catch (JsonMappingException jme) {
            throw new SerializationException("Unable to deserialize String " + outputType.getClass().getSimpleName() + " instance", jme);
        } catch (IOException ie) {
            throw new SerializationException("Unable to deserialize String to " + outputType.getClass().getSimpleName() + " instance", ie);
        }
    }

}
