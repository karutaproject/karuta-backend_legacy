# Changing PDF font

These steps are mostly for non-ASCII characters to be displayed correctly when generating a PDF.

1. Fetch fop-2.1 http://www.apache.org/dyn/closer.cgi/xmlgraphics/fop
2. Fetch the font you want to use (in this example _ipaexm00301.zip_
3. Unzip everything in a work folder, the structure should look like
> ./fop-2.1</br>
> ./ipaexm00301

4. Inside the fop-2.1 folder, type
> java -cp "build/fop.jar:./lib/avalon-framework-impl-4.3.1.jar:./lib/commons-logging-1.0.4.jar:./lib/commons-io-1.3.1.jar:./lib/xmlgraphics-commons-2.1.jar" org.apache.fop.fonts.apps.TTFReader ../ipaexm00301/ipaexm.ttf ../ipaexm.xml

5. Move the font file and generated xml inside the karuta-backend\_config folder (or other), and/or define the files location inside "_fopuserconfig.xml_"
> \<font metrics-url={xml\_file} embed-url={ttf\_file}>

6. Set the font family name inside "_fopuserconfig.xml_", in this example, "_IPAexMincho_"
> \<font-triplet name="IPAexMincho" style="normal" weight="normal"/></br>
> \<font-triplet name="IPAexMincho" style="normal" weight="bold"/></br>
> \<font-triplet name="IPAexMincho" style="italic" weight="normal"/></br>
> \<font-triplet name="IPAexMincho" style="italic" weight="bold"/>`

7. Set the font family to use inside the XSL file on the UI part "_/karuta/karuta/xsl/xmlportfolio2fo.xsl_"
> \<fo:root font-family="IPAexMincho">

8. Restart the backend service.
9. Generate a PDF, characters should be displayed correctly.