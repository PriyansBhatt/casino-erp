package com.casino.casinoerp.controller;

import com.casino.casinoerp.service.RolePermissionService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/role-permissions")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    public RolePermissionController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }
    
}