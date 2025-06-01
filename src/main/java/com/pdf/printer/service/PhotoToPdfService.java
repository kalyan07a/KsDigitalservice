package com.pdf.printer.service;


import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

@Service
public class PhotoToPdfService {

    // Dimensions in mm (converted to points)
    private static final float IMAGE_WIDTH_MM = 35f;
    private static final float IMAGE_HEIGHT_MM = 45f;
    private static final float MARGIN_MM = 10f;
    private static final float GAP_MM = 5f;
    private static final int IMAGES_PER_ROW = 5;  // Changed to 6 images per row
    private static final int IMAGES_PER_PAGE = 25; // Changed to 36 images per page (6x6)
    private static final float DPI = 300f;

    // Convert mm to points (1mm = 2.83465 points)
    private float mmToPoints(float mm) {
        return mm * 2.83465f;
    }

    public void generatePdf(MultipartFile photo, int copies, OutputStream outputStream) throws IOException {
        try {
            // Read original image with high quality
            BufferedImage originalImage = ImageIO.read(photo.getInputStream());
            
            // Calculate target dimensions in pixels based on DPI
            float mmToInch = 25.4f;
            int targetWidthPx = (int)(IMAGE_WIDTH_MM / mmToInch * DPI);
            int targetHeightPx = (int)(IMAGE_HEIGHT_MM / mmToInch * DPI);
            
            // High-quality resizing with anti-aliasing
            BufferedImage resizedImage = Scalr.resize(originalImage, 
                Scalr.Method.ULTRA_QUALITY,
                Scalr.Mode.FIT_EXACT,
                targetWidthPx,
                targetHeightPx,
                Scalr.OP_ANTIALIAS);

            // High-quality JPEG compression
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            ImageWriter writer = writers.next();
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(1.0f); // Maximum quality
            
            writer.write(null, new IIOImage(resizedImage, null, null), param);
            writer.dispose();
            
            // Create PDF image with high resolution
            Image pdfImage = Image.getInstance(baos.toByteArray());
            pdfImage.scaleAbsolute(mmToPoints(IMAGE_WIDTH_MM), mmToPoints(IMAGE_HEIGHT_MM));

            // Calculate page size for 6x6 arrangement
            float pageWidth = mmToPoints(IMAGE_WIDTH_MM * IMAGES_PER_ROW + GAP_MM * (IMAGES_PER_ROW - 1) + MARGIN_MM * 2);
            float pageHeight = mmToPoints(IMAGE_HEIGHT_MM * 6 + GAP_MM * 5 + MARGIN_MM * 2); // 6 rows with 5 gaps between them

            // Create PDF document with high quality
            Document document = new Document(new Rectangle(pageWidth, pageHeight), 
                mmToPoints(MARGIN_MM), mmToPoints(MARGIN_MM), 
                mmToPoints(MARGIN_MM), mmToPoints(MARGIN_MM));
            
            PdfWriter writerInstance = PdfWriter.getInstance(document, outputStream);
            writerInstance.setCompressionLevel(0); // No compression for maximum quality
            document.open();

            // Initial positions
            float startX = mmToPoints(MARGIN_MM);
            float startY = pageHeight - mmToPoints(MARGIN_MM + IMAGE_HEIGHT_MM);
            
            float currentX = startX;
            float currentY = startY;
            int currentPageImages = 0;

            for (int i = 0; i < copies; i++) {
                if (currentPageImages >= IMAGES_PER_PAGE) {
                    document.newPage();
                    currentX = startX;
                    currentY = startY;
                    currentPageImages = 0;
                }

                // Add image to PDF
                pdfImage.setAbsolutePosition(currentX, currentY);
                document.add(pdfImage);
                currentPageImages++;

                // Move to next position
                currentX += mmToPoints(IMAGE_WIDTH_MM + GAP_MM);

                // Move to next row if current row is full
                if (currentPageImages % IMAGES_PER_ROW == 0) {
                    currentX = startX;
                    currentY -= mmToPoints(IMAGE_HEIGHT_MM + GAP_MM);
                }
            }

            document.close();
        } catch (DocumentException e) {
            throw new IOException("Error generating PDF", e);
        } catch (Exception e) {
            throw new IOException("Error processing image", e);
        }
    }
}