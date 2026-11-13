package com.hongyu.water.doc.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hongyu.water.config.AppProperties;

/**
 * 根据 app.nas.type 创建网盘适配器。新增网盘产品时在这里注册新的 {@link NasClient} 实现。
 */
@Configuration
public class NasConfig {

    @Bean
    NasClient nasClient(AppProperties props) {
        AppProperties.Nas nas = props.nas();
        String type = nas == null || nas.type() == null ? "none" : nas.type().trim().toLowerCase();
        return switch (type) {
            case "", "none" -> new DisabledNasClient();
            case "mount" -> new MountedFolderNasClient(nas.root());
            // case "webdav" -> new WebDavNasClient(...);   预留：网盘仅支持 WebDAV 时实现
            // case "api"    -> new VendorApiNasClient(...); 预留：按网盘厂商开放接口实现
            default -> throw new IllegalStateException("不支持的网盘类型 app.nas.type=" + type + "，可选：none / mount");
        };
    }
}
