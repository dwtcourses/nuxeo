/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.ecm.automation.server.jaxrs.io;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.DataHandler;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.Blob;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class MultipartBlobs extends MimeMultipart {

    private static final Pattern BOUNDARY = Pattern.compile(";\\s*boundary\\s*=\"([^\"]+)\"");

    public MultipartBlobs() {
        super("mixed");
    }

    public MultipartBlobs(List<Blob> blobs) throws Exception {
        super("mixed");
        addBlobs(blobs);
    }

    public void addBlobs(List<Blob> blobs) throws Exception {
        for (Blob blob : blobs) {
            addBlob(blob);
        }
    }

    public void addBlob(Blob blob) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new InputStreamDataSource(
                blob.getStream(), blob.getMimeType(), blob.getFilename())));
        part.setDisposition("attachment");
        String filename = blob.getFilename();
        if (filename != null) {
            part.setFileName(filename);
        }
        // must set the mime type at end because setFileName is also setting a
        // wrong content type.
        String mimeType = blob.getMimeType();
        if (mimeType != null) {
            part.setHeader("Content-type", mimeType);
        }

        addBodyPart(part);
    }

    public String getBoundary() {
        Matcher m = BOUNDARY.matcher(getContentType());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public Response getResponse() {
        // jersey is not correctly reading ctype string -> it is removing quote
        // from values..
        // we need to rebuild ourself the correct header to preserve quotes on
        // boundary value (otherwise javax.mail will not work on client side)
        // for this we use our own MediaType class
        return Response.ok(this).type(new BoundaryMediaType(getContentType())).build();
    }

    /**
     * hack to be able to output boundary with quotes if needed.
     *
     * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
     *
     */
    static class BoundaryMediaType extends MediaType {
        private final String ctype;

        public BoundaryMediaType(String ctype) {
            super("multipart", "mixed");
            this.ctype = ctype;
        }

        @Override
        public String toString() {
            return ctype;
        }
    }

}