/**
 * NOTE: This class is auto generated by the swagger code generator program (2.4.10).
 * https://github.com/swagger-api/swagger-codegen
 * Do not edit the class manually.
 */
package io.swagger.api;

import io.swagger.annotations.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2019-12-30T07:09:15.005Z")

@Api(value = "ranking", description = "the ranking API")
public interface RankingApi {

    @ApiOperation(value = "Returns the ranking (current or at specific game)", nickname = "getRanking", notes = "", response = String.class, authorizations = {
        @Authorization(value = "api_key")
    }, tags={ "Ranking", })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "successful operation", response = String.class) })
    @RequestMapping(value = "/ranking",
        produces = { "application/json", "text/plain" }, 
        method = RequestMethod.GET)
    ResponseEntity<String> getRanking();


    @ApiOperation(value = "Returns the ranking (current or at specific game)", nickname = "getRankingAtGame", notes = "", authorizations = {
        @Authorization(value = "api_key")
    }, tags={ "Ranking", })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "successful operation") })
    @RequestMapping(value = "/ranking/{gameId}",
        produces = { "application/json" }, 
        method = RequestMethod.GET)
    ResponseEntity<Void> getRankingAtGame(@ApiParam(value = "ID of game to produce the ranking",required=true) @PathVariable("gameId") Long gameId);

}