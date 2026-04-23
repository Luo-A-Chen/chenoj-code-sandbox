package org.example.chenojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;

/**
 * 仅用于本机验证「Java 能否连上 Docker」的小示例，与 {@code /executeCode} 沙箱无关。
 * <p>
 * 启动 Spring 沙箱请运行 {@link org.example.chenojcodesandbox.ChenojCodeSandboxApplication}，
 * 勿把本类的运行配置当成服务启动。
 */
public class DockerDemo {

    /** 官方最小演示镜像，需先 pull 再 create（与之前写死 nginx 且未 pull 导致的 404 区分开）。 */
    private static final String IMAGE = "hello-world:latest";

    public static void main(String[] args) throws Exception {
        DockerClient client = DockerClientBuilder.getInstance().build();

        System.out.println("拉取镜像: " + IMAGE);
        client.pullImageCmd(IMAGE)
                .exec(new PullImageResultCallback() {
                    @Override
                    public void onNext(PullResponseItem item) {
                        if (item.getStatus() != null) {
                            System.out.println(item.getStatus());
                        }
                        super.onNext(item);
                    }
                })
                .awaitCompletion();

        CreateContainerResponse created = client.createContainerCmd(IMAGE).exec();
        String id = created.getId();
        System.out.println("创建容器: " + id);

        client.startContainerCmd(id).exec();
        System.out.println("已启动（hello-world 会立刻退出，可在 docker ps -a 里看记录）。");
    }
}
