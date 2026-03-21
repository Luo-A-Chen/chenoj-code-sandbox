package org.example.chenojcodesandbox.security;

import java.security.Permission;

/**
 * 默认安全管理器
 */
public class DenySecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission permission){
        super.checkPermission(permission);
    }
}
