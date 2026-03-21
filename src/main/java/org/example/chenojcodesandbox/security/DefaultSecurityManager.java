package org.example.chenojcodesandbox.security;

import java.security.Permission;

/**
 * 默认安全管理器
 * 内置了很多方法用来检查用户的权限
 */
public class DefaultSecurityManager extends SecurityManager{
    @Override
    public void checkPermission(Permission perm){
//        super.checkPermission(perm);
    }
}
