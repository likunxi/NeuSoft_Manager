package com.team22.aiguanli.security;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class LoginUser implements Serializable {
    private Long id;
    private String username;
    private String displayName;
    private String roleName;
    private Set<String> permissions = new HashSet<>();

    public boolean has(String perm) {
        return permissions.contains(perm);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
}
