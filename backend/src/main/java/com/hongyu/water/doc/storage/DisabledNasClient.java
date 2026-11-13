package com.hongyu.water.doc.storage;

import java.util.List;

import org.springframework.core.io.Resource;

import com.hongyu.water.common.BizException;

/**
 * 未对接网盘（app.nas.type = none）。
 */
public class DisabledNasClient implements NasClient {

    @Override
    public String type() {
        return "none";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String describe() {
        return "未对接网盘";
    }

    @Override
    public List<NasEntry> list(int maxFiles) {
        throw new BizException("尚未对接内部网盘，请在服务器配置 NAS_TYPE 和 NAS_ROOT 后重启服务");
    }

    @Override
    public Resource open(String path) {
        throw new BizException("尚未对接内部网盘，无法读取网盘文件");
    }
}
