package com.fongmi.android.NewTV.server.process;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import com.fongmi.android.NewTV.server.Nano;
import com.fongmi.android.NewTV.server.impl.Process;
import com.fongmi.android.NewTV.utils.ImgUtil;

import java.io.ByteArrayInputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Image implements Process {

    private static final String PATH = "/image/";

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith(PATH);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        ImgUtil.Image image = ImgUtil.getImage(url.substring(PATH.length()));
        if (image == null) return Nano.error(Status.NOT_FOUND, "");
        return newFixedLengthResponse(Status.OK, image.mime(), new ByteArrayInputStream(image.data()), image.data().length);
    }
}
