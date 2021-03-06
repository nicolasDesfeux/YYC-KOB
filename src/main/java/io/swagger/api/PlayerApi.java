/**
 * NOTE: This class is auto generated by the swagger code generator program (2.4.10).
 * https://github.com/swagger-api/swagger-codegen
 * Do not edit the class manually.
 */
package io.swagger.api;

import io.swagger.annotations.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2020-01-02T22:35:02.857Z")

@Api(value = "player", description = "the player API")
public interface PlayerApi {

    @ApiOperation(value = "Add a new player", nickname = "addPlayer", notes = "", tags = {"Player",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "New player succesfully added"),
            @ApiResponse(code = 405, message = "Invalid input")})
    @RequestMapping(value = "/player",
            produces = {"application/json"},
            consumes = {"application/x-www-form-urlencoded"},
            method = RequestMethod.POST)
    ResponseEntity<String> addPlayer(@ApiParam(value = "Name of the new player", required = true) @RequestParam(value = "name", required = true) String name);


    @ApiOperation(value = "Deletes a player", nickname = "deletePlayer", notes = "", tags = {"Player",})
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid ID supplied"),
            @ApiResponse(code = 404, message = "Player not found")})
    @RequestMapping(value = "/player/{playerId}",
            method = RequestMethod.DELETE)
    ResponseEntity<Void> deletePlayer(@ApiParam(value = "player id to delete", required = true) @PathVariable("playerId") Long playerId);


    @ApiOperation(value = "Finds Player by name", nickname = "findPlayerByName", notes = "Will look for any player matching the name exactly (not case sensitive)", response = String.class, tags = {"Player",})
    @ApiResponses(value = {
            @ApiResponse(code = 302, message = "Player was found and returned", response = String.class),
            @ApiResponse(code = 404, message = "Couldn't find a player matching that name. ")})
    @RequestMapping(value = "/player/findByName",
            produces = {"application/json"},
            method = RequestMethod.GET)
    ResponseEntity<String> findPlayerByName(@NotNull @ApiParam(value = "Name to search for", required = true) @Valid @RequestParam(value = "name", required = true) String name);


    @ApiOperation(value = "Finds a Player by Id", nickname = "getPlayerById", notes = "Returns a single player", response = String.class, tags = {"Player",})
    @ApiResponses(value = {
            @ApiResponse(code = 302, message = "Player found", response = String.class),
            @ApiResponse(code = 404, message = "Player not found")})
    @RequestMapping(value = "/player/{playerId}",
            produces = {"application/json"},
            method = RequestMethod.GET)
    ResponseEntity<String> getPlayerById(@ApiParam(value = "ID of player to return", required = true) @PathVariable("playerId") Long playerId);


    @ApiOperation(value = "Updates a player name", nickname = "updatePlayerWithForm", notes = "", tags = {"Player",})
    @ApiResponses(value = {
            @ApiResponse(code = 405, message = "Invalid input")})
    @RequestMapping(value = "/player/{playerId}",
            produces = {"application/json"},
            consumes = {"application/x-www-form-urlencoded"},
            method = RequestMethod.POST)
    ResponseEntity<String> updatePlayerWithForm(@ApiParam(value = "ID of player that needs to be updated", required = true) @PathVariable("playerId") Long playerId, @ApiParam(value = "Updated name of the player", required = true) @RequestParam(value = "name", required = true) String name);

}
