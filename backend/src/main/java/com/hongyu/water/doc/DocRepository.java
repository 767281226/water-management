package com.hongyu.water.doc;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.hongyu.water.doc.storage.StorageType;

public interface DocRepository extends JpaRepository<Doc, Long>, JpaSpecificationExecutor<Doc> {

    Optional<Doc> findByIdAndDeletedFalse(Long id);

    List<Doc> findByIdIn(Collection<Long> ids);

    List<Doc> findByStorageTypeAndDeletedFalse(StorageType type);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    /** 各分类上架资料数：[categoryId, count] */
    @Query("select d.categoryId, count(d) from Doc d where d.deleted = false and d.status = :status group by d.categoryId")
    List<Object[]> countByCategory(Doc.Status status);

    @Modifying
    @Query("update Doc d set d.viewCount = d.viewCount + 1 where d.id = :id")
    void incrementView(Long id);

    @Modifying
    @Query("update Doc d set d.downloadCount = d.downloadCount + 1 where d.id = :id")
    void incrementDownload(Long id);
}
