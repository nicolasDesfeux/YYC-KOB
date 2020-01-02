package io.swagger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2020-01-02T22:35:02.857Z")

@Controller
public class ResultApiController implements ResultApi {

    private static final Logger log = LoggerFactory.getLogger(ResultApiController.class);

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    public ResultApiController(ObjectMapper objectMapper, HttpServletRequest request) {
        this.objectMapper = objectMapper;
        this.request = request;
    }

    public ResponseEntity<Void> addResult(@ApiParam(value = "Game for the result", required = true) @RequestParam(value = "gameId", required = true) String gameId, @ApiParam(value = "Player that participate in the game", required = true) @RequestParam(value = "playerId", required = true) String playerId, @ApiParam(value = "Result of the player for thegame", required = true) @RequestParam(value = "result", required = true) Integer result) {
        String accept = request.getHeader("Accept");
        return new ResponseEntity<Void>(HttpStatus.NOT_IMPLEMENTED);
    }

    public ResponseEntity<Void> deleteResult(@ApiParam(value = "Result id to delete", required = true) @PathVariable("resultId") Long resultId) {
        String accept = request.getHeader("Accept");
        return new ResponseEntity<Void>(HttpStatus.NOT_IMPLEMENTED);
    }

    public ResponseEntity<String> getResultById(@ApiParam(value = "ID of game to return", required = true) @PathVariable("resultId") Long resultId) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("")) {
            try {
                return new ResponseEntity<String>(objectMapper.readValue("", String.class), HttpStatus.NOT_IMPLEMENTED);
            } catch (IOException e) {
                log.error("Couldn't serialize response for content type ", e);
                return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<String>(HttpStatus.NOT_IMPLEMENTED);
    }

    public ResponseEntity<Void> updateResultWithForm(@ApiParam(value = "ID of result that needs to be updated", required = true) @PathVariable("resultId") Long resultId, @ApiParam(value = "Game for the result", required = true) @RequestParam(value = "gameId", required = true) String gameId, @ApiParam(value = "Player that participate in the game", required = true) @RequestParam(value = "playerId", required = true) String playerId, @ApiParam(value = "Result of the player for the game", required = true) @RequestParam(value = "result", required = true) Integer result) {
        String accept = request.getHeader("Accept");
        return new ResponseEntity<Void>(HttpStatus.NOT_IMPLEMENTED);
    }

}
