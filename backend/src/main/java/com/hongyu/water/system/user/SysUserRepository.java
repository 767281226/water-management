package com.hongyu.water.system.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SysUserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

    @EntityGraph(attributePaths = "roles")
    Optional<SysUser> findWithRolesByIdAndDeletedFalse(Long id);

    /** 登录：支持账号（不区分大小写）或工号 */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from SysUser u where u.deleted = false and (lower(u.username) = lower(:account) or u.employeeNo = :account)")
    List<SysUser> findForLogin(String account);

    @Query("select count(u) > 0 from SysUser u where u.deleted = false and lower(u.username) = lower(:username) "
            + "and u.id <> :excludeId")
    boolean usernameTaken(String username, long excludeId);

    @Query("select count(u) > 0 from SysUser u where u.deleted = false and u.employeeNo = :employeeNo and u.id <> :excludeId")
    boolean employeeNoTaken(String employeeNo, long excludeId);

    long countByDeptIdInAndDeletedFalse(Collection<Long> deptIds);

    /** 各部门人数：[deptId, count] */
    @Query("select u.deptId, count(u) from SysUser u where u.deleted = false and u.deptId is not null group by u.deptId")
    List<Object[]> countGroupByDept();

    @Modifying
    @Query("update SysUser u set u.deptId = null where u.deleted = true and u.deptId = :deptId")
    int detachDeletedFromDept(Long deptId);

    List<SysUser> findByDeletedFalseAndStatusOrderByIdAsc(UserStatus status);

    List<SysUser> findByIdIn(Collection<Long> ids);
}
