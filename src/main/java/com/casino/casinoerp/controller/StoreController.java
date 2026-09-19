package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.StoreDtos.*;
import com.casino.casinoerp.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/store")
public class StoreController {
    private final StoreService service;
    public StoreController(StoreService service) { this.service=service; }
    private <T> ApiResponse<T> ok(T value) {return ApiResponse.success("Store",value);}
    @GetMapping("/items") public ApiResponse<Page<Item>> items(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Boolean active,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ok(service.items(q,active,page,size));}
    @GetMapping("/staff") public ApiResponse<Page<Staff>> staff(@RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ok(service.staff(q,page,size));}
    @GetMapping("/requests") public ApiResponse<Page<Request>> requests(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ok(service.requests(q,status,page,size));}
    @GetMapping("/requests/{id}") public ApiResponse<Detail> detail(@PathVariable UUID id) {return ok(service.detail(id));}
    @GetMapping("/procurements") public ApiResponse<Page<Procurement>> procurements(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ok(service.procurements(q,status,page,size));}
    @GetMapping("/movements") public ApiResponse<Page<Movement>> movements(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String type,
        @RequestParam(required=false) UUID itemId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ok(service.movements(q,type,itemId,page,size));}
    @PostMapping("/items") public ApiResponse<Receipt> item(@Valid @RequestBody ItemWrite r) {return ok(service.item(null,r));}
    @PatchMapping("/items/{id}") public ApiResponse<Receipt> item(@PathVariable UUID id,@Valid @RequestBody ItemWrite r) {return ok(service.item(id,r));}
    @PostMapping("/requests") public ApiResponse<Receipt> request(@Valid @RequestBody RequestCreate r) {return ok(service.createRequest(r));}
    @PostMapping("/requests/{id}/cancel-remaining") public ApiResponse<Receipt> cancel(@PathVariable UUID id,@Valid @RequestBody Transition r) {return ok(service.cancel(id,r));}
    @PostMapping("/request-lines/{id}/procurements") public ApiResponse<Receipt> procure(@PathVariable UUID id,@Valid @RequestBody Quantity r) {return ok(service.procure(id,r));}
    @PostMapping("/request-lines/{id}/issues") public ApiResponse<Receipt> issue(@PathVariable UUID id,@Valid @RequestBody Quantity r) {return ok(service.issue(id,r));}
    @PostMapping("/items/{id}/opening-stock") public ApiResponse<Receipt> opening(@PathVariable UUID id,@Valid @RequestBody Quantity r) {return ok(service.opening(id,r));}
    @PostMapping("/items/{id}/adjustments") public ApiResponse<Receipt> adjust(@PathVariable UUID id,@Valid @RequestBody Adjustment r) {return ok(service.adjust(id,r));}
    @PostMapping("/procurements/{id}/order") public ApiResponse<Receipt> order(@PathVariable UUID id,@Valid @RequestBody Transition r) {return ok(service.transition(id,r,true));}
    @PostMapping("/procurements/{id}/cancel") public ApiResponse<Receipt> cancelProcurement(@PathVariable UUID id,@Valid @RequestBody Transition r) {return ok(service.transition(id,r,false));}
    @PostMapping("/procurements/{id}/receive") public ApiResponse<Receipt> receive(@PathVariable UUID id,@Valid @RequestBody Quantity r) {return ok(service.receive(id,r));}
}
