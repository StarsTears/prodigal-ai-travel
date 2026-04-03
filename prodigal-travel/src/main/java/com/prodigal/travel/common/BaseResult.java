package com.prodigal.travel.common;

import com.prodigal.travel.exception.ResponseStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 基础响应类
 * @since 2026/3/31
 */
@Data
public class BaseResult <T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private int code;
    private boolean status;
    private String msg="巭(gu)孬(nao)嫑(biao)哔哔···";
    private T data;

    public BaseResult() {
    }
    public BaseResult(Integer code, Boolean status, String msg, T data) {
        this.code = code;
        this.status = status;
        this.msg = msg;
        this.data = data;
    }

    public BaseResult(ResponseStatus status) {
        this(status.getCode(), false, status.getMessage(),null);
    }

    public  BaseResult<T> code(int code){
        this.code = code;
        return this;
    }
    public  BaseResult<T> status(boolean status){
        this.status = status;
        return this;
    }
    public  BaseResult<T> msg(String msg){
        this.msg = msg;
        return this;
    }
    public  BaseResult<T> data(T data){
        this.data = data;
        return this;
    }
}
