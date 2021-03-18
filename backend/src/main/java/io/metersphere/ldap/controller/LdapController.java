package io.metersphere.ldap.controller;

import io.metersphere.base.domain.User;
import io.metersphere.commons.constants.ParamConstants;
import io.metersphere.commons.constants.UserSource;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.RsaKey;
import io.metersphere.commons.utils.RsaUtil;
import io.metersphere.controller.ResultHolder;
import io.metersphere.controller.request.LoginRequest;
import io.metersphere.i18n.Translator;
import io.metersphere.ldap.service.LdapService;
import io.metersphere.service.SystemParameterService;
import io.metersphere.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.security.NoSuchAlgorithmException;

@RestController
@RequestMapping("/ldap")
public class LdapController {

    @Resource
    private UserService userService;
    @Resource
    private LdapService ldapService;
    @Resource
    private SystemParameterService systemParameterService;

    @PostMapping(value = "/signin")
    public ResultHolder login(@RequestBody LoginRequest request) throws NoSuchAlgorithmException {

        String isOpen = systemParameterService.getValue(ParamConstants.LDAP.OPEN.getValue());
        if (StringUtils.isBlank(isOpen) || StringUtils.equals(Boolean.FALSE.toString(), isOpen)) {
            MSException.throwException(Translator.get("ldap_authentication_not_enabled"));
        }

        DirContextOperations dirContext = ldapService.authenticate(request);
        String email = ldapService.getMappingAttr("email", dirContext);
        String userId = ldapService.getMappingAttr("username", dirContext);

        SecurityUtils.getSubject().getSession().setAttribute("authenticate", UserSource.LDAP.name());
        SecurityUtils.getSubject().getSession().setAttribute("email", email);


        if (StringUtils.isBlank(email)) {
            MSException.throwException(Translator.get("login_fail_email_null"));
        }

        // userId 或 email 有一个相同即为存在本地用户
        User u = userService.selectUser(userId, email);
        if (u == null) {

            // 新建用户 获取LDAP映射属性
            String name = ldapService.getMappingAttr("name", dirContext);
            String phone = ldapService.getNotRequiredMappingAttr("phone", dirContext);

            User user = new User();
            user.setId(userId);
            user.setName(name);
            user.setEmail(email);

            if (StringUtils.isNotBlank(phone)) {
                user.setPhone(phone);
            }

            user.setSource(UserSource.LDAP.name());
            userService.addLdapUser(user);
        }

        // 执行 ShiroDBRealm 中 LDAP 登录逻辑
        LoginRequest loginRequest = new LoginRequest();
        RsaKey rsaKey = CommonBeanFactory.getBean(RsaKey.class);
        loginRequest.setUsername(RsaUtil.publicEncrypt(userId,rsaKey.getPublicKey()));
        return userService.login(loginRequest);
    }

    @PostMapping("/test/connect")
    public void testConnect() {
        ldapService.testConnect();
    }

    @PostMapping("/test/login")
    public void testLogin(@RequestBody LoginRequest request) {
        ldapService.authenticate(request);
    }

    @GetMapping("/open")
    public boolean isOpen() {
        return ldapService.isOpen();
    }

}
