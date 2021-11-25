/* =======================================================
	Copyright 2014 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

package com.portfolio.data.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtils {
    private static final Logger logger = LoggerFactory.getLogger(LogUtils.class);
    static boolean hasLoaded = false;
    static String filePath = "";

    /// The folder we use is {CATALINA.BASE}/logs/{SERVLET-NAME}_logs
    public static boolean initDirectory(ServletContext context) throws Exception {
        if (hasLoaded) return true;
        try {
            /// Preparing logfile for direct access
            final String servName = context.getContextPath();
            /// Check if folder exists
            File logFolder = new File(System.getProperty("catalina.base") + "/logs/" + servName + "_logs");
            if (logFolder.mkdirs()) {
                logger.info("Log folder {} was created", logFolder.getCanonicalPath());
            }

            filePath = logFolder.getCanonicalPath();
            hasLoaded = true;
        } catch (Exception e) {
            logger.error("Can't create folder: {}", filePath, e);
            throw new ServletException(e);
        }
        return hasLoaded;
    }

    public static BufferedWriter getLog(String filename) throws IOException {
        // Ensure directory exists
        File dir = new File(filePath);
        if (!dir.exists())
            dir.mkdirs();
        // Ensure file exists
        File file = new File(filePath + filename);
        if (!file.exists())
            file.createNewFile();

        FileOutputStream fos = new FileOutputStream(file, true);
        OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        BufferedWriter bwrite = new BufferedWriter(osw);

        return bwrite;
    }

    public static String getCurrentDate() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        return dateFormat.format(date);
    }
}