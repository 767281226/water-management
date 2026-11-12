package com.hongyu.water.system.role;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    boolean existsByCode(String code);

    List<SysRole> findAllByOrderBySortAscIdAsc();

    List<SysRole> findByIdIn(Collection<Long> ids);

    @Query(value = "select count(*) from sys_user_role ur join sys_user u on u.id = ur.user_id "
            + "where ur.role_id = :roleId and not u.deleted", nativeQuery = true)
    long countUsers(Long roleId);
}
