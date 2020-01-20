package io.swagger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dto.Player;
import io.swagger.annotations.ApiParam;
import kob.KOB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.IOException;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2020-01-02T22:35:02.857Z")

@Controller
public class PlayerApiController implements PlayerApi {

    private static final Logger log = LoggerFactory.getLogger(PlayerApiController.class);

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    public PlayerApiController(ObjectMapper objectMapper, HttpServletRequest request) {
        this.objectMapper = objectMapper;
        this.request = request;
    }

    public ResponseEntity<String> addPlayer(@ApiParam(value = "Name of the new player", required = true) @RequestParam(value = "name", required = true) String name) {
        String accept = request.getHeader("Accept");

        if (accept != null && accept.contains("application/json")) {
            Player p = new Player(name);
            p = p.save();
            if (p != null) {
                try {
                    return new ResponseEntity<String>(p.toJSONString(), HttpStatus.ACCEPTED);
                } catch (IOException e) {
                    log.error("Couldn't serialize response for content type ", e);
                    return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                return new ResponseEntity<String>(HttpStatus.NOT_ACCEPTABLE);
            }
        } else {
            return new ResponseEntity<String>(HttpStatus.NOT_IMPLEMENTED);
        }
    }

    public ResponseEntity<Void> deletePlayer(@ApiParam(value = "player id to delete", required = true) @PathVariable("playerId") Long playerId) {
        String accept = request.getHeader("Accept");
        return new ResponseEntity<Void>(HttpStatus.NOT_IMPLEMENTED);
    }

    public ResponseEntity<String> findPlayerByName(@NotNull @ApiParam(value = "Name to search for", required = true) @Valid @RequestParam(value = "name", required = true) String name) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<String>(KOB.getInstance().getPlayerDao().getPlayerByName(name).toJSONString(), HttpStatus.FOUND);
            } catch (IOException e) {
                log.error("Couldn't serialize response for content type ", e);
                return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<String>(HttpStatus.NOT_IMPLEMENTED);
    }

    public ResponseEntity<String> getPlayerById(@ApiParam(value = "ID of player to return", required = true) @PathVariable("playerId") Long playerId) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            try {
                return new ResponseEntity<String>(KOB.getInstance().getPlayerDao().getPlayer(playerId).toJSONString(), HttpStatus.FOUND);
            } catch (IOException e) {
                log.error("Couldn't serialize response for content type ", e);
                return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<String>(HttpStatus.NOT_IMPLEMENTED);
    }

    public ResponseEntity<String> updatePlayerWithForm(@ApiParam(value = "ID of player that needs to be updated", required = true) @PathVariable("playerId") Long playerId, @ApiParam(value = "Updated name of the player", required = true) @RequestParam(value = "name", required = true) String name) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            Player p = KOB.getInstance().getPlayerDao().getPlayer(playerId);
            p.setName(name);
            p.save();
            return new ResponseEntity<String>(HttpStatus.OK);
        }
        return new ResponseEntity<String>(HttpStatus.NOT_IMPLEMENTED);
    }

}
