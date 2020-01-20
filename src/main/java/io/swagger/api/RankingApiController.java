package io.swagger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import kob.KOB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2020-01-02T22:35:02.857Z")

@Controller
public class RankingApiController implements RankingApi {

    private static final Logger log = LoggerFactory.getLogger(RankingApiController.class);

    private final ObjectMapper objectMapper;

    private final HttpServletRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    public RankingApiController(ObjectMapper objectMapper, HttpServletRequest request) {
        this.objectMapper = objectMapper;
        this.request = request;
    }

    public ResponseEntity<String> getRanking(@ApiParam(value = "Game at which the ranking needs to be generated. Leave empty for most current Ranking") @Valid @RequestParam(value = "gameId", required = false) Long gameId) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("")) {
            return new ResponseEntity<String>(KOB.getInstance().getPrintableRanking(gameId), HttpStatus.OK);
        }

        return new ResponseEntity<String>(HttpStatus.NOT_IMPLEMENTED);
    }

}
