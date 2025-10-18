package com.fabianacevedo.ionicchromecast;

import com.getcapacitor.Logger;

public class IonicChromecast {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
