package org.example.chenojcodesandbox.security;

import java.security.Permission;

/**
 * 本项目安全管理器
 */
public class MySecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission permission){
//        super.checkPermission(permission);
    }

    /**
     * 检测程序是否可执行文件，拒绝执行命令
     * @param cmd
     */
    @Override
    public void checkExec(String cmd){
        throw new SecurityException("不允许执行命令" + cmd);
    }

    /**
     * 检测程序是否读取文件，拒绝读取文件
     * @param file
     */
    @Override
    public void checkRead(String file){
        System.out.println(file);
        if(file.contains("hutool")){
            return;
        }
        throw new SecurityException("不允许读取文件" + file);
    }

    /**
     * 检测程序是否写入文件，拒绝写入文件
     * @param file
     */
    @Override
    public void checkWrite(String file){
        throw new SecurityException("不允许写入文件" + file);
    }

    /**
     * 检测程序是否删除文件，拒绝删除文件
     * @param file
     */
    @Override
    public void checkDelete(String file){
        throw new SecurityException("不允许删除文件" + file);
    }

    /**
     * 检测程序是否允许链接网络，拒绝链接网络
     * @param host port
     */
    @Override
    public void checkConnect(String host, int port){
        throw new SecurityException("不允许链接网络" + host + ":" + port);
    }
}
