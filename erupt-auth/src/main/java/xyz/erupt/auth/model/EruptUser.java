package xyz.erupt.auth.model;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import xyz.erupt.annotation.Erupt;
import xyz.erupt.annotation.EruptField;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.EditType;
import xyz.erupt.annotation.sub_field.View;
import xyz.erupt.annotation.sub_field.sub_edit.BoolType;
import xyz.erupt.annotation.sub_field.sub_edit.ReferenceTreeType;
import xyz.erupt.annotation.sub_field.sub_edit.Search;
import xyz.erupt.auth.util.MD5Utils;
import xyz.erupt.core.model.BaseModel;

import javax.persistence.*;
import java.util.Date;
import java.util.Set;

/**
 * @author liyuepeng
 * @date 2018-11-22.
 */
@Entity
@Table(name = "E_USER", uniqueConstraints = {
        @UniqueConstraint(columnNames = "account")
})
@Erupt(
        name = "用户",
        desc = "用户配置",
        dataProxy = EruptUser.class
)
@Getter
@Setter
@Component
public class EruptUser extends BaseModel implements DataProxy<EruptUser> {

    @EruptField(
            views = @View(title = "用户名", sortable = true),
            edit = @Edit(title = "用户名", desc = "登录用户名", notNull = true)
    )
    private String account;

    @EruptField(
            views = @View(title = "姓名", sortable = true),
            edit = @Edit(title = "姓名", notNull = true, search = @Search(value = true, vague = true))
    )
    private String name;

    @ManyToOne
    @JoinColumn(name = "ERUPT_MENU_ID")
    @EruptField(
            views = @View(title = "首页地址", column = "name"),
            edit = @Edit(
                    title = "首页地址",
                    type = EditType.REFERENCE_TREE,
                    referenceTreeType = @ReferenceTreeType(pid = "parentMenu.id")
            )
    )
    private EruptMenu eruptMenu;

    @Transient
    @EruptField(
            edit = @Edit(title = "密码", type = EditType.DIVIDE)
    )
    private String pwdDivide;

    @EruptField(
            edit = @Edit(title = "密码")
    )
    private String password;

    @Transient
    @EruptField(
            edit = @Edit(title = "确认密码")
    )
    private String password2;

    @EruptField(
            views = @View(title = "md5加密"),
            edit = @Edit(
                    title = "md5加密",
                    type = EditType.BOOLEAN,
                    boolType = @BoolType(
                            trueText = "加密",
                            falseText = "不加密"
                    ),
                    search = @Search(true)
            )
    )
    private Boolean isMd5;

//    @EruptField(
//            views = @View(title = "联系电话", sortable = true),
//            edit = @Edit(title = "联系电话", notNull = true, search = @Search(value = true, vague = true))
//    )
//    private String phone;
//
//    @EruptField(
//            views = @View(title = "邮箱", sortable = true),
//            edit = @Edit(title = "邮箱", notNull = true, search = @Search(value = true, vague = true))
//    )
//    private String email;
//
//    @EruptField(
//            views = @View(title = "身份证号", sortable = true),
//            edit = @Edit(title = "身份证号", notNull = true, search = @Search(value = true, vague = true))
//    )
//    private String identity;

    @EruptField(
            views = @View(title = "账户状态"),
            edit = @Edit(
                    title = "账户状态",
                    type = EditType.BOOLEAN,
                    boolType = @BoolType(
                            trueText = "激活",
                            falseText = "锁定"
                    )
            )
    )
    private Boolean status;

    @Lob
    @EruptField(
            edit = @Edit(
                    title = "ip白名单",
                    desc = "ip与ip之间使用换行符间隔，不填表示不鉴权",
                    type = EditType.TEXTAREA
            )

    )
    private String whiteIp;

    @Lob
    @EruptField(
            edit = @Edit(
                    title = "备注",
                    type = EditType.TEXTAREA,
                    search = @Search(value = true, vague = true)
            )
    )
    private String remark;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "E_USER_ROLE",
            joinColumns = @JoinColumn(name = "USER_ID", referencedColumnName = "ID"),
            inverseJoinColumns = @JoinColumn(name = "ROLE_ID", referencedColumnName = "ID"))
    @EruptField(
            edit = @Edit(
                    title = "所属角色",
                    type = EditType.TAB_TREE
            )
    )
    private Set<EruptRole> roles;

    private Boolean isAdmin;

    private Date createTime;

    @Transient
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void editBehavior(EruptUser eruptUser) {
        eruptUser.setPassword(null);
    }

    @Override
    public void beforeAdd(EruptUser eruptUser) {
        if (StringUtils.isBlank(eruptUser.getPassword())) {
            throw new RuntimeException("密码必填");
        }
        if (eruptUser.getPassword().equals(eruptUser.getPassword2())) {
            eruptUser.setIsAdmin(false);
            eruptUser.setCreateTime(new Date());
            if (eruptUser.getIsMd5()) {
                eruptUser.setPassword(MD5Utils.digest(eruptUser.getPassword()));
            }
        } else {
            throw new RuntimeException("两次密码输入不一致");
        }
    }

    @Override
    public void beforeUpdate(EruptUser eruptUser) {
        entityManager.clear();
        EruptUser eu = entityManager.find(EruptUser.class, eruptUser.getId());
        if (StringUtils.isNotBlank(eruptUser.getPassword())) {
            if (!eruptUser.getPassword().equals(eruptUser.getPassword2())) {
                throw new RuntimeException("两次密码输入不一致");
            }
            if (eruptUser.getIsMd5()) {
                eruptUser.setPassword(MD5Utils.digest(eruptUser.getPassword()));
            } else {
                eruptUser.setPassword(eruptUser.getPassword());
            }
        } else {
            eruptUser.setPassword(eu.getPassword());
        }
    }


}
