package com.huatai.careeragent.report.pdf;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("career-agent.report.pdf")
public class PdfExportProperties {
    private String exportDir = "./data/exports";
    private String fontPath = "";

    public String getExportDir() { return exportDir; }
    public void setExportDir(String exportDir) { this.exportDir = exportDir; }
    public String getFontPath() { return fontPath; }
    public void setFontPath(String fontPath) { this.fontPath = fontPath; }
}
