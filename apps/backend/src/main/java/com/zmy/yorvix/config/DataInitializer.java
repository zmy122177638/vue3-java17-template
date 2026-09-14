package com.zmy.yorvix.config;

import com.zmy.yorvix.mapper.sys.SysUserMapper;
import com.zmy.yorvix.model.sys.SysUser;
import com.zmy.yorvix.security.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 首次启动初始化内置账号（密码实时散列，避免硬编码散列值）。
 * <p>内置账号：admin（通用模板初始管理员）
 * <p>可通过 yorvix.init.seed-enabled=false 关闭（生产环境）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yorvix.init", name = "seed-enabled",
    havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

  private final SysUserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(String... args) {
    if (userMapper.selectCount(null) > 0) {
      return;
    }
    createUser("admin", "123456", "管理员");
    log.info("内置账号初始化完成: admin（初始密码见代码常量，请尽快修改）");
  }

  private void createUser(String username, String rawPassword, String nickname) {
    SysUser user = new SysUser();
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setNickname(nickname);
    user.setStatus(1);
    userMapper.insert(user);
  }
}
