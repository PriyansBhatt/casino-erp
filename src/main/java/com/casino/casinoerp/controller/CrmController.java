package com.casino.casinoerp.controller;
import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.dto.CrmDtos.*;
import com.casino.casinoerp.service.CrmService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/crm")
public class CrmController {
 private final CrmService service;public CrmController(CrmService service){this.service=service;}
 @GetMapping("/records") public ApiResponse<History> history(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String type,@RequestParam(defaultValue="")String status,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to){return ApiResponse.success("CRM records",service.history(q,type,status,from,to));}
 @GetMapping("/customers") public ApiResponse<List<Map<String,Object>>> customers(@RequestParam(defaultValue="")String q){return ApiResponse.success("Customers",service.customers(q));}
 @PostMapping("/services") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<Map<String,Object>> service(@Valid @RequestBody ServiceCreate request){return ApiResponse.success("Service recorded",service.create(request));}
 @PostMapping("/transport") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<Map<String,Object>> transport(@Valid @RequestBody TransportCreate request){return ApiResponse.success("Transport recorded",service.create(request));}
 @PatchMapping("/services/{id}/status") public ApiResponse<Map<String,Object>> serviceStatus(@PathVariable UUID id,@Valid @RequestBody Change request){return ApiResponse.success("Status recorded",service.change(false,id,request));}
 @PatchMapping("/transport/{id}/status") public ApiResponse<Map<String,Object>> transportStatus(@PathVariable UUID id,@Valid @RequestBody Change request){return ApiResponse.success("Status recorded",service.change(true,id,request));}
}
