package org.example.chenojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.core.DockerClientBuilder;

public class dockerDemo {
    public static void main(String[] args) {
//        获取一个所有参数都是默认值的docker client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        PingCmd pingCmd = dockerClient.pingCmd();
        System.out.println("hello docker");
    }
}
