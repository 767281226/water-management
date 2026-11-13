package com.hongyu.water.doc.storage;

/**
 * 资料文件的存放位置。
 */
public enum StorageType {
    /** 系统上传，保存在服务器本地目录 */
    LOCAL,
    /** 内部网盘，系统只建立索引，文件仍在网盘中 */
    NAS
}
