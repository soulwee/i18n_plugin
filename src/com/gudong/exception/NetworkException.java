package com.gudong.exception;

/**
 * description
 *
 * @author maggie
 * @date 2021-07-21 15:32
 */
public class NetworkException extends RuntimeException {
    public NetworkException(String msg) {
        super(msg);
    }
}