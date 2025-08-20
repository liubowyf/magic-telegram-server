#!/bin/bash

# 检查MongoDB中的session数据
echo "正在检查MongoDB中的session数据..."

# 使用Spring Boot的MongoDB连接来查询数据
cat > temp-session-check.java << 'EOF'
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import com.telegram.server.repository.TelegramSessionRepository;
import com.telegram.server.entity.TelegramSession;
import java.util.List;

@SpringBootApplication
public class SessionChecker implements CommandLineRunner {
    
    @Autowired
    private TelegramSessionRepository sessionRepository;
    
    public static void main(String[] args) {
        SpringApplication.run(SessionChecker.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== MongoDB Session数据检查 ===");
        
        List<TelegramSession> sessions = sessionRepository.findAll();
        System.out.println("总session数量: " + sessions.size());
        
        for (TelegramSession session : sessions) {
            System.out.println("\n--- Session信息 ---");
            System.out.println("手机号: " + session.getPhoneNumber());
            System.out.println("认证状态: " + session.getAuthState());
            System.out.println("是否活跃: " + session.getIsActive());
            System.out.println("实例ID: " + session.getInstanceId());
            System.out.println("最后活跃时间: " + session.getLastActiveTime());
            
            // 检查数据库文件
            if (session.getDatabaseFiles() != null) {
                System.out.println("数据库文件数量: " + session.getDatabaseFiles().size());
                for (String fileName : session.getDatabaseFiles().keySet()) {
                    System.out.println("  - " + fileName);
                }
            } else {
                System.out.println("数据库文件: 无");
            }
            
            // 检查下载文件
            if (session.getDownloadedFiles() != null) {
                System.out.println("下载文件数量: " + session.getDownloadedFiles().size());
            } else {
                System.out.println("下载文件: 无");
            }
        }
        
        System.out.println("\n=== 检查完成 ===");
        System.exit(0);
    }
}
EOF

echo "临时检查文件已创建，请手动运行Spring Boot应用来检查MongoDB数据"
echo "或者使用以下命令启动应用并查看session数据:"
echo "mvn spring-boot:run -s temp-settings.xml"