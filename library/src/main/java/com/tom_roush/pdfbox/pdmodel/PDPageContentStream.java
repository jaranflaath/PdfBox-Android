/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.pdfbox.pdmodel;

import android.graphics.Path;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Stack;

import com.tom_roush.harmony.awt.AWTColor;
import com.tom_roush.harmony.awt.geom.AffineTransform;
import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSNumber;
import com.tom_roush.pdfbox.pdfwriter.COSWriter;
import com.tom_roush.pdfbox.pdmodel.common.PDStream;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColorSpace;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDInlineImage;
import com.tom_roush.pdfbox.pdmodel.graphics.pattern.PDTilingPattern;
import com.tom_roush.pdfbox.pdmodel.graphics.shading.PDShading;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import com.tom_roush.pdfbox.util.Charsets;
import com.tom_roush.pdfbox.util.Matrix;
import com.tom_roush.pdfbox.util.NumberFormatUtil;

/**
 * Provides the ability to write to a page content stream.
 *
 * @author Ben Litchfield
 */
public final class PDPageContentStream implements Closeable
{

    /**
     * This is to choose what to do with the stream: overwrite, append or prepend.
     */
    public static enum AppendMode
    {
        /**
         * Overwrite the existing page content streams.
         */
        OVERWRITE,
        /**
         * Append the content stream after all existing page content streams.
         */
        APPEND,
        /**
         * Insert before all other page content streams.
         */
        PREPEND;

        public boolean isOverwrite()
        {
            return this == OVERWRITE;
        }

        public boolean isPrepend()
        {
            return this == PREPEND;
        }
    }

    private final PDDocument document;
    private OutputStream output;
    private PDResources resources;

    private boolean inTextMode = false;
    private final Stack<PDFont> fontStack = new Stack<PDFont>();

    private final Stack<PDColorSpace> nonStrokingColorSpaceStack = new Stack<PDColorSpace>();
    private final Stack<PDColorSpace> strokingColorSpaceStack = new Stack<PDColorSpace>();

    // number format
    private final NumberFormat formatDecimal = NumberFormat.getNumberInstance(Locale.US);
    private final byte[] formatBuffer = new byte[32];

    /**
     * Create a new PDPage content stream. This constructor overwrites all existing content streams
     * of this page.
     *
     * @param document The document the page is part of.
     * @param sourcePage The page to write the contents to.
     * @throws IOException If there is an error writing to the page contents.
     */
    public PDPageContentStream(PDDocument document, PDPage sourcePage) throws IOException
    {
        this(document, sourcePage, AppendMode.OVERWRITE, true, false);
    }

    /**
     * Create a new PDPage content stream.
     *
     * @param document The document the page is part of.
     * @param sourcePage The page to write the contents to.
     * @param appendContent Indicates whether content will be overwritten. If false all previous
     *                      content is deleted.
     * @param compress Tell if the content stream should compress the page contents.
     * @throws IOException If there is an error writing to the page contents.
     * @deprecated use {@link #PDPageContentStream(PDDocument, PDPage, PDPageContentStream.AppendMode, boolean)}
     */
    @Deprecated
    public PDPageContentStream(PDDocument document, PDPage sourcePage, boolean appendContent,
        boolean compress) throws IOException
    {
        this(document, sourcePage, appendContent, compress, false);
    }

    /**
     * Create a new PDPage content stream. If the appendContent parameter is set to
     * {@link AppendMode#APPEND}, you may want to use
     * {@link #PDPageContentStream(PDDocument, PDPage, PDPageContentStream.AppendMode, boolean, boolean)}
     * instead, with the fifth parameter set to true.
     *
     * @param document The document the page is part of.
     * @param sourcePage The page to write the contents to.
     * @param appendContent Indicates whether content will be overwritten, appended or prepended.
     * @param compress Tell if the content stream should compress the page contents.
     * @throws IOException If there is an error writing to the page contents.
     */
    public PDPageContentStream(PDDocument document, PDPage sourcePage, AppendMode appendContent,
        boolean compress) throws IOException
    {
        this(document, sourcePage, appendContent, compress, false);
    }

    /**
     * Create a new PDPage content stream.
     *
     * @param document The document the page is part of.
     * @param sourcePage The page to write the contents to.
     * @param appendContent Indicates whether content will be overwritten. If false all previous
     *                      content is deleted.
     * @param compress Tell if the content stream should compress the page contents.
     * @param resetContext Tell if the graphic context should be reseted. You should use this when
     * appending to an existing stream, because the existing stream may have changed graphic
     * properties (e.g. scaling, rotation).
     * @throws IOException If there is an error writing to the page contents.
     * @deprecated use {@link #PDPageContentStream(PDDocument, PDPage, PDPageContentStream.AppendMode, boolean, boolean) }
     */
    @Deprecated
    public PDPageContentStream(PDDocument document, PDPage sourcePage, boolean appendContent,
        boolean compress, boolean resetContext) throws IOException
    {
        this (document, sourcePage, appendContent ? AppendMode.APPEND : AppendMode.OVERWRITE, compress, resetContext);
    }

    /**
     * Create a new PDPage content stream.
     *
     * @param document The document the page is part of.
     * @param sourcePage The page to write the contents to.
     * @param appendContent Indicates whether content will be overwritten, appended or prepended.
     * @param compress Tell if the content stream should compress the page contents.
     * @param resetContext Tell if the graphic context should be reset. This is only relevant when
     * the appendContent parameter is set to {@link AppendMode#APPEND}. You should use this when
     * appending to an existing stream, because the existing stream may have changed graphic
     * properties (e.g. scaling, rotation).
     * @throws IOException If there is an error writing to the page contents.
     */
    public PDPageContentStream(PDDocument document, PDPage sourcePage, AppendMode appendContent,
        boolean compress, boolean resetContext) throws IOException
    {
        this.document = document;
        COSName filter = compress ? COSName.FLATE_DECODE : null;

        // If request specifies the need to append/prepend to the document
        if (!appendContent.isOverwrite() && sourcePage.hasContents())
        {
            // Create a stream to append new content
            PDStream contentsToAppend = new PDStream(document);

            // Add new stream to contents array
            COSBase contents = sourcePage.getCOSObject().getDictionaryObject(COSName.CONTENTS);
            COSArray array;
            if (contents instanceof COSArray)
            {
                // If contents is already an array, a new stream is simply appended to it
                array = (COSArray) contents;
            }
            else
            {
                // Creates a new array and adds the current stream plus a new one to it
                array = new COSArray();
                array.add(contents);
            }
            if (appendContent.isPrepend())
            {
                array.add(0, contentsToAppend.getCOSObject());
            }
            else
            {
                array.add(contentsToAppend);
            }

            // save the initial/unmodified graphics context
            if (resetContext)
            {
                // create a new stream to encapsulate the existing stream
                PDStream saveGraphics = new PDStream(document);
                output = saveGraphics.createOutputStream(filter);

                // save the initial/unmodified graphics context
                saveGraphicsState();
                close();

                // insert the new stream at the beginning
                array.add(0, saveGraphics.getCOSObject());
            }

            // Sets the compoundStream as page contents
            sourcePage.getCOSObject().setItem(COSName.CONTENTS, array);
            output = contentsToAppend.createOutputStream(filter);

            // restore the initial/unmodified graphics context
            if (resetContext)
            {
                restoreGraphicsState();
            }
        }
        else
        {
            if (sourcePage.hasContents())
            {
                Log.w("PdfBox-Android", "You are overwriting an existing content, you should use the append mode");
            }
            PDStream contents = new PDStream(document);
            sourcePage.setContents(contents);
            output = contents.createOutputStream(filter);
        }

        // this has to be done here, as the resources will be set to null when resetting the content
        // stream
        resources = sourcePage.getResources();
        if (resources == null)
        {
            resources = new PDResources();
            sourcePage.setResources(resources);
        }

        // configure NumberFormat
        formatDecimal.setMaximumFractionDigits(5);
        formatDecimal.setGroupingUsed(false);
    }

    /**
     * Create a new appearance stream. Note that this is not actually a "page" content stream.
     *
     * @param doc The document the page is part of.
     * @param appearance The appearance stream to write to.
     * @throws IOException If there is an error writing to the page contents.
     */
    public PDPageContentStream(PDDocument doc, PDAppearanceStream appearance) throws IOException
    {
        this (doc, appearance, appearance.getStream().createOutputStream());
    }

    /**
     * Create a new appearance stream. Note that this is not actually a "page" content stream.
     *
     * @param doc The document the appearance is part of.
     * @param appearance The appearance stream to add to.
     * @param outputStream The appearances output stream to write to.
     * @throws IOException If there is an error writing to the page contents.
     */
    public PDPageContentStream(PDDocument doc, PDAppearanceStream appearance, OutputStream outputStream)
        throws IOException
    {
        this.document = doc;

        output = outputStream;
        this.resources = appearance.getResources();

        formatDecimal.setMaximumFractionDigits(4);
        formatDecimal.setGroupingUsed(false);
    }

    /**
     * Create a new appearance stream. Note that this is not actually a "page" content stream.
     *
     * @param doc The document the appearance is part of.
     * @param form The XObject form to add to.
     * @param outputStream The output stream to write to.
     * @throws IOException If there is an error writing to the page contents.
     */
    public PDPageContentStream(PDDocument doc, PDFormXObject form, OutputStream outputStream)
        throws IOException
    {
        this.document = doc;

        output = outputStream;
        this.resources = form.getResources();

        formatDecimal.setMaximumFractionDigits(4);
        formatDecimal.setGroupingUsed(false);
    }

    /**
     * Create a new appearance stream. Note that this is not actually a "page" content stream.
     *
     * @param doc The document the appearance is part of.
     * @param pattern The pattern to add to.
     * @param outputStream The output stream to write to.
     * @throws IOException If there is an error writing to the page contents.
     */
    public PDPageContentStream(PDDocument doc, PDTilingPattern pattern, OutputStream outputStream)
        throws IOException
    {
        this.document = doc;

        output = outputStream;
        this.resources = pattern.getResources();

        formatDecimal.setMaximumFractionDigits(4);
        formatDecimal.setGroupingUsed(false);
    }

    /**
     * Begin some text operations.
     *
     * @throws IOException If there is an error writing to the stream or if you attempt to
     *         nest beginText calls.
     * @throws IllegalStateException If the method was not allowed to be called at this time.
     */
    public void beginText() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: Nested beginText() calls are not allowed.");
        }
        writeOperator("BT");
        inTextMode = true;
    }

    /**
     * End some text operations.
     *
     * @throws IOException If there is an error writing to the stream or if you attempt to
     *         nest endText calls.
     * @throws IllegalStateException If the method was not allowed to be called at this time.
     */
    public void endText() throws IOException
    {
        if (!inTextMode)
        {
            throw new IllegalStateException("Error: You must call beginText() before calling endText.");
        }
        writeOperator("ET");
        inTextMode = false;
    }

    /**
     * Set the font and font size to draw text with.
     *
     * @param font The font to use.
     * @param fontSize The font size to draw the text.
     * @throws IOException If there is an error writing the font information.
     */
    public void setFont(PDFont font, float fontSize) throws IOException
    {
        if (fontStack.isEmpty())
        {
            fontStack.add(font);
        }
        else
        {
            fontStack.setElementAt(font, fontStack.size() - 1);
        }

        if (font.willBeSubset())
        {
            document.getFontsToSubset().add(font);
        }

        writeOperand(resources.add(font));
        writeOperand(fontSize);
        writeOperator("Tf");
    }

    /**
     * This will draw a string at the current location on the screen.
     *
     * @param text The text to draw.
     * @throws IOException If an io exception occurs.
     * @deprecated Use {@link #showText} instead.
     */
    @Deprecated
    public void drawString(String text) throws IOException
    {
        showText(text);
    }

    /**
     * Shows the given text at the location specified by the current text matrix with the given
     * interspersed positioning. This allows the user to efficiently position each glyph or sequence
     * of glyphs.
     *
     * @param textWithPositioningArray An array consisting of String and Float types. Each String is
     * output to the page using the current text matrix. Using the default coordinate system, each
     * interspersed number adjusts the current text matrix by translating to the left or down for
     * horizontal and vertical text respectively. The number is expressed in thousands of a text
     * space unit, and may be negative.
     *
     * @throws IOException if an io exception occurs.
     */
    public void showTextWithPositioning(Object[] textWithPositioningArray) throws IOException
    {
        write("[");
        for (Object obj : textWithPositioningArray)
        {
            if (obj instanceof String)
            {
                showTextInternal((String) obj);
            }
            else if (obj instanceof Float)
            {
                writeOperand((Float) obj);
            }
            else
            {
                throw new IllegalArgumentException("Argument must consist of array of Float and String types");
            }
        }
        write("] ");
        writeOperator("TJ");
    }

    /**
     * Shows the given text at the location specified by the current text matrix.
     *
     * @param text The Unicode text to show.
     * @throws IOException If an io exception occurs.
     * @throws IllegalArgumentException if a character isn't supported by the current font
     */
    public void showText(String text) throws IOException
    {
        showTextInternal(text);
        write(" ");
        writeOperator("Tj");
    }

    /**
     * Outputs a string using the correct encoding and subsetting as required.
     *
     * @param text The Unicode text to show.
     *
     * @throws IOException If an io exception occurs.
     */
    protected void showTextInternal(String text) throws IOException
    {
        if (!inTextMode)
        {
            throw new IllegalStateException("Must call beginText() before showText()");
        }

        if (fontStack.isEmpty())
        {
            throw new IllegalStateException("Must call setFont() before showText()");
        }

        PDFont font = fontStack.peek();

        // Unicode code points to keep when subsetting
        if (font.willBeSubset())
        {
            int offset = 0;
            while (offset < text.length())
            {
                int codePoint = text.codePointAt(offset);
                font.addToSubset(codePoint);
                offset += Character.charCount(codePoint);
            }
        }

        COSWriter.writeString(font.encode(text), output);
    }

    /**
     * Sets the text leading.
     *
     * @param leading The leading in unscaled text units.
     * @throws IOException If there is an error writing to the stream.
     * @deprecated use {@link #setLeading(float) setLeading(float)}
     */
    @Deprecated
    public void setLeading(double leading) throws IOException
    {
        setLeading((float) leading);
    }

    /**
     * Sets the text leading.
     *
     * @param leading The leading in unscaled text units.
     * @throws IOException If there is an error writing to the stream.
     */
    public void setLeading(float leading) throws IOException
    {
        writeOperand(leading);
        writeOperator("TL");
    }

    /**
     * Move to the start of the next line of text. Requires the leading (see {@link #setLeading})
     * to have been set.
     *
     * @throws IOException If there is an error writing to the stream.
     */
    public void newLine() throws IOException
    {
        if (!inTextMode)
        {
            throw new IllegalStateException("Must call beginText() before newLine()");
        }
        writeOperator("T*");
    }

    /**
     * The Td operator.
     * A current text matrix will be replaced with a new one (1 0 0 1 x y).
     * @param tx The x translation.
     * @param ty The y translation.
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #newLineAtOffset} instead.
     */
    @Deprecated
    public void moveTextPositionByAmount(float tx, float ty) throws IOException
    {
        newLineAtOffset(tx, ty);
    }

    /**
     * The Td operator.
     * Move to the start of the next line, offset from the start of the current line by (tx, ty).
     *
     * @param tx The x translation.
     * @param ty The y translation.
     * @throws IOException If there is an error writing to the stream.
     * @throws IllegalStateException If the method was not allowed to be called at this time.
     */
    public void newLineAtOffset(float tx, float ty) throws IOException
    {
        if (!inTextMode)
        {
            throw new IllegalStateException("Error: must call beginText() before newLineAtOffset()");
        }
        writeOperand(tx);
        writeOperand(ty);
        writeOperator("Td");
    }

    /**
     * The Tm operator. Sets the text matrix to the given values.
     * A current text matrix will be replaced with the new one.
     * @param a The a value of the matrix.
     * @param b The b value of the matrix.
     * @param c The c value of the matrix.
     * @param d The d value of the matrix.
     * @param e The e value of the matrix.
     * @param f The f value of the matrix.
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #setTextMatrix(Matrix)} instead.
     */
    @Deprecated
    public void setTextMatrix(double a, double b, double c, double d, double e, double f) throws IOException
    {
        setTextMatrix(new Matrix((float)a, (float)b, (float)c, (float)d, (float)e, (float)f));
    }

    /**
     * The Tm operator. Sets the text matrix to the given values.
     * A current text matrix will be replaced with the new one.
     * @param matrix the transformation matrix
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #setTextMatrix(Matrix)} instead.
     */
    @Deprecated
    public void setTextMatrix(AffineTransform matrix) throws IOException
    {
        setTextMatrix(new Matrix(matrix));
    }

    /**
     * The Tm operator. Sets the text matrix to the given values.
     * A current text matrix will be replaced with the new one.
     *
     * @param matrix the transformation matrix
     * @throws IOException If there is an error writing to the stream.
     * @throws IllegalStateException If the method was not allowed to be called at this time.
     */
    public void setTextMatrix(Matrix matrix) throws IOException
    {
        if (!inTextMode)
        {
            throw new IllegalStateException("Error: must call beginText() before setTextMatrix");
        }
        writeAffineTransform(matrix.createAffineTransform());
        writeOperator("Tm");
    }

    /**
     * The Tm operator. Sets the text matrix to the given scaling and translation values.
     * A current text matrix will be replaced with the new one.
     * @param sx The scaling factor in x-direction.
     * @param sy The scaling factor in y-direction.
     * @param tx The translation value in x-direction.
     * @param ty The translation value in y-direction.
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #setTextMatrix(Matrix)} instead.
     */
    @Deprecated
    public void setTextScaling(double sx, double sy, double tx, double ty) throws IOException
    {
        setTextMatrix(new Matrix((float) sx, 0f, 0f, (float) sy, (float) tx, (float) ty));
    }

    /**
     * The Tm operator. Sets the text matrix to the given translation values.
     * A current text matrix will be replaced with the new one.
     * @param tx The translation value in x-direction.
     * @param ty The translation value in y-direction.
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #setTextMatrix(Matrix)} instead.
     */
    @Deprecated
    public void setTextTranslation(double tx, double ty) throws IOException
    {
        setTextMatrix(Matrix.getTranslateInstance((float) tx, (float) ty));
    }

    /**
     * The Tm operator. Sets the text matrix to the given rotation and translation values.
     * A current text matrix will be replaced with the new one.
     * @param angle The angle used for the counterclockwise rotation in radians.
     * @param tx The translation value in x-direction.
     * @param ty The translation value in y-direction.
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #setTextMatrix(Matrix)} instead.
     */
    @Deprecated
    public void setTextRotation(double angle, double tx, double ty) throws IOException
    {
        setTextMatrix(Matrix.getRotateInstance(angle, (float) tx, (float) ty));
    }

    /**
     * Draw an image at the x,y coordinates, with the default size of the image.
     *
     * @param image The image to draw.
     * @param x The x-coordinate to draw the image.
     * @param y The y-coordinate to draw the image.
     *
     * @throws IOException If there is an error writing to the stream.
     */
    public void drawImage(PDImageXObject image, float x, float y) throws IOException
    {
        drawImage(image, x, y, image.getWidth(), image.getHeight());
    }

    /**
     * Draw an image at the x,y coordinates, with the given size.
     *
     * @param image The image to draw.
     * @param x The x-coordinate to draw the image.
     * @param y The y-coordinate to draw the image.
     * @param width The width to draw the image.
     * @param height The height to draw the image.
     *
     * @throws IOException If there is an error writing to the stream.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void drawImage(PDImageXObject image, float x, float y, float width, float height) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: drawImage is not allowed within a text block.");
        }

        saveGraphicsState();

        AffineTransform transform = new AffineTransform(width, 0, 0, height, x, y);
        transform(new Matrix(transform));

        writeOperand(resources.add(image));
        writeOperator("Do");

        restoreGraphicsState();
    }

    /**
     * Draw an image at the origin with the given transformation matrix.
     *
     * @param image The image to draw.
     * @param matrix The transformation matrix to apply to the image.
     *
     * @throws IOException If there is an error writing to the stream.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void drawImage(PDImageXObject image, Matrix matrix) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: drawImage is not allowed within a text block.");
        }

        saveGraphicsState();

        AffineTransform transform = matrix.createAffineTransform();
        transform(new Matrix(transform));

        writeOperand(resources.add(image));
        writeOperator("Do");

        restoreGraphicsState();
    }

    /**
     * Draw an inline image at the x,y coordinates, with the default size of the image.
     *
     * @param inlineImage The inline image to draw.
     * @param x The x-coordinate to draw the inline image.
     * @param y The y-coordinate to draw the inline image.
     *
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #drawImage(PDInlineImage, float, float)} instead.
     */
    @Deprecated
    public void drawInlineImage(PDInlineImage inlineImage, float x, float y) throws IOException
    {
        drawImage(inlineImage, x, y, inlineImage.getWidth(), inlineImage.getHeight());
    }

    /**
     * Draw an inline image at the x,y coordinates, with the default size of the image.
     *
     * @param inlineImage The inline image to draw.
     * @param x The x-coordinate to draw the inline image.
     * @param y The y-coordinate to draw the inline image.
     *
     * @throws IOException If there is an error writing to the stream.
     */
    public void drawImage(PDInlineImage inlineImage, float x, float y) throws IOException
    {
        drawImage(inlineImage, x, y, inlineImage.getWidth(), inlineImage.getHeight());
    }

    /**
     * Draw an inline image at the x,y coordinates and a certain width and height.
     *
     * @param inlineImage The inline image to draw.
     * @param x The x-coordinate to draw the inline image.
     * @param y The y-coordinate to draw the inline image.
     * @param width The width of the inline image to draw.
     * @param height The height of the inline image to draw.
     *
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #drawImage(PDInlineImage, float, float, float, float)} instead.
     */
    @Deprecated
    public void drawInlineImage(PDInlineImage inlineImage, float x, float y, float width, float height) throws IOException
    {
        drawImage(inlineImage, x, y, width, height);
    }

    /**
     * Draw an inline image at the x,y coordinates and a certain width and height.
     *
     * @param inlineImage The inline image to draw.
     * @param x The x-coordinate to draw the inline image.
     * @param y The y-coordinate to draw the inline image.
     * @param width The width of the inline image to draw.
     * @param height The height of the inline image to draw.
     *
     * @throws IOException If there is an error writing to the stream.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void drawImage(PDInlineImage inlineImage, float x, float y, float width, float height) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: drawImage is not allowed within a text block.");
        }

        saveGraphicsState();
        transform(new Matrix(width, 0, 0, height, x, y));

        // create the image dictionary
        StringBuilder sb = new StringBuilder();
        sb.append("BI");

        sb.append("\n /W ");
        sb.append(inlineImage.getWidth());

        sb.append("\n /H ");
        sb.append(inlineImage.getHeight());

        sb.append("\n /CS ");
        sb.append("/");
        sb.append(inlineImage.getColorSpace().getName());

        if (inlineImage.getDecode() != null && inlineImage.getDecode().size() > 0)
        {
            sb.append("\n /D ");
            sb.append("[");
            for (COSBase base : inlineImage.getDecode())
            {
                sb.append(((COSNumber) base).intValue());
                sb.append(" ");
            }
            sb.append("]");
        }

        if (inlineImage.isStencil())
        {
            sb.append("\n /IM true");
        }

        sb.append("\n /BPC ");
        sb.append(inlineImage.getBitsPerComponent());

        // image dictionary
        write(sb.toString());
        writeLine();

        // binary data
        writeOperator("ID");
        writeBytes(inlineImage.getData());
        writeLine();
        writeOperator("EI");

        restoreGraphicsState();
    }

    /**
     * Draw an xobject(form or image) at the x,y coordinates and a certain width and height.
     *
     * @param xobject The xobject to draw.
     * @param x The x-coordinate to draw the image.
     * @param y The y-coordinate to draw the image.
     * @param width The width of the image to draw.
     * @param height The height of the image to draw.
     *
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #drawImage} instead.
     */
    @Deprecated
    public void drawXObject(PDXObject xobject, float x, float y, float width, float height) throws IOException
    {
        AffineTransform transform = new AffineTransform(width, 0, 0, height, x, y);
        drawXObject(xobject, transform);
    }

    /**
     * Draw an xobject(form or image) using the given {@link AffineTransform} to position
     * the xobject.
     *
     * @param xobject The xobject to draw.
     * @param transform the transformation matrix
     * @throws IOException If there is an error writing to the stream.
     * @throws IllegalStateException If the method was called within a text block.
     * @deprecated Use {@link #drawImage(PDImageXObject, Matrix) drawImage(PDImageXObject, Matrix)}
     * or {@link #drawForm(PDFormXObject) drawForm(PDFormXObject)} with
     * {@link #transform(Matrix) transform(Matrix)} instead.
     */
    @Deprecated
    public void drawXObject(PDXObject xobject, AffineTransform transform) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: drawXObject is not allowed within a text block.");
        }

        String xObjectPrefix;
        if (xobject instanceof PDImageXObject)
        {
            xObjectPrefix = "Im";
        }
        else
        {
            xObjectPrefix = "Form";
        }
        COSName objMapping = resources.add(xobject, xObjectPrefix);

        saveGraphicsState();
        transform(new Matrix(transform));

        writeOperand(objMapping);
        writeOperator("Do");

        restoreGraphicsState();
    }

    /**
     * Draws the given Form XObject at the current location.
     *
     * @param form Form XObject
     * @throws IOException if the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void drawForm(PDFormXObject form) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: drawForm is not allowed within a text block.");
        }

        writeOperand(resources.add(form));
        writeOperator("Do");
    }

    /**
     * The cm operator. Concatenates the current transformation matrix with the given values.
     * @param a The a value of the matrix.
     * @param b The b value of the matrix.
     * @param c The c value of the matrix.
     * @param d The d value of the matrix.
     * @param e The e value of the matrix.
     * @param f The f value of the matrix.
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #transform} instead.
     */
    @Deprecated
    public void concatenate2CTM(double a, double b, double c, double d, double e, double f) throws IOException
    {
        transform(new Matrix((float) a, (float) b, (float) c, (float) d, (float) e, (float) f));
    }

    /**
     * The cm operator. Concatenates the current transformation matrix with the given
     * {@link AffineTransform}.
     * @param at the transformation matrix
     * @throws IOException If there is an error writing to the stream.
     * @deprecated Use {@link #transform} instead.
     */
    @Deprecated
    public void concatenate2CTM(AffineTransform at) throws IOException
    {
        transform(new Matrix(at));
    }

    /**
     * The cm operator. Concatenates the given matrix with the CTM.
     *
     * @param matrix the transformation matrix
     * @throws IOException If there is an error writing to the stream.
     */
    public void transform(Matrix matrix) throws IOException
    {
        if (inTextMode)
        {
            Log.w("PdfBox-Android", "Modifying the current transformation matrix is not allowed within text objects.");
        }

        writeAffineTransform(matrix.createAffineTransform());
        writeOperator("cm");
    }

    /**
     * q operator. Saves the current graphics state.
     * @throws IOException If an error occurs while writing to the stream.
     */
    public void saveGraphicsState() throws IOException
    {
        if (inTextMode)
        {
            Log.w("PdfBox-Android", "Saving the graphics state is not allowed within text objects.");
        }

        if (!fontStack.isEmpty())
        {
            fontStack.push(fontStack.peek());
        }
        if (!strokingColorSpaceStack.isEmpty())
        {
            strokingColorSpaceStack.push(strokingColorSpaceStack.peek());
        }
        if (!nonStrokingColorSpaceStack.isEmpty())
        {
            nonStrokingColorSpaceStack.push(nonStrokingColorSpaceStack.peek());
        }
        writeOperator("q");
    }

    /**
     * Q operator. Restores the current graphics state.
     * @throws IOException If an error occurs while writing to the stream.
     */
    public void restoreGraphicsState() throws IOException
    {
        if (inTextMode)
        {
            Log.w("PdfBox-Android", "Restoring the graphics state is not allowed within text objects.");
        }

        if (!fontStack.isEmpty())
        {
            fontStack.pop();
        }
        if (!strokingColorSpaceStack.isEmpty())
        {
            strokingColorSpaceStack.pop();
        }
        if (!nonStrokingColorSpaceStack.isEmpty())
        {
            nonStrokingColorSpaceStack.pop();
        }
        writeOperator("Q");
    }

    /**
     * Set the stroking color space.  This will add the colorspace to the PDResources
     * if necessary.
     *
     * @param colorSpace The colorspace to write.
     * @throws IOException If there is an error writing the colorspace.
     * @deprecated Use {@link #setStrokingColor} instead.
     */
    @Deprecated
    public void setStrokingColorSpace(PDColorSpace colorSpace) throws IOException
    {
        setStrokingColorSpaceStack(colorSpace);
        writeOperand(getName(colorSpace));
        writeOperator("CS");
    }

    /**
     * Set the stroking color space.  This will add the colorspace to the PDResources
     * if necessary.
     *
     * @param colorSpace The colorspace to write.
     * @throws IOException If there is an error writing the colorspace.
     * @deprecated Use {@link #setNonStrokingColor(PDColor)} instead.
     */
    @Deprecated
    public void setNonStrokingColorSpace(PDColorSpace colorSpace) throws IOException
    {
        setNonStrokingColorSpaceStack(colorSpace);
        writeOperand(getName(colorSpace));
        writeOperator("cs");
    }

    private COSName getName(PDColorSpace colorSpace) throws IOException
    {
        if (colorSpace instanceof PDDeviceGray ||
            colorSpace instanceof PDDeviceRGB /*||
            colorSpace instanceof PDDeviceCMYK TODO: PdfBox-Android*/)
        {
            return COSName.getPDFName(colorSpace.getName());
        }
        else
        {
            return resources.add(colorSpace);
        }
    }

    /**
     * Sets the stroking color and, if necessary, the stroking color space.
     *
     * @param color Color in a specific color space.
     * @throws IOException If an IO error occurs while writing to the stream.
     */
    public void setStrokingColor(PDColor color) throws IOException
    {
        if (strokingColorSpaceStack.isEmpty() ||
            strokingColorSpaceStack.peek() != color.getColorSpace())
        {
            writeOperand(getName(color.getColorSpace()));
            writeOperator("CS");
            setStrokingColorSpaceStack(color.getColorSpace());
        }

        for (float value : color.getComponents())
        {
            writeOperand(value);
        }

//        if (color.getColorSpace() instanceof PDPattern)
//        {
//            writeOperand(color.getPatternName());
//        }

//        if (color.getColorSpace() instanceof PDPattern ||
//            color.getColorSpace() instanceof PDSeparation ||
//            color.getColorSpace() instanceof PDDeviceN ||
//            color.getColorSpace() instanceof PDICCBased)
//        {
//            writeOperator("SCN");
//        }
//        else
//        {
            writeOperator("SC");
//        } TODO: PdfBox-Android
    }

    /**
     * Set the stroking color using an AWT color. Conversion uses the default sRGB color space.
     *
     * @param color The color to set.
     * @throws IOException If an IO error occurs while writing to the stream.
     */
    public void setStrokingColor(AWTColor color) throws IOException
    {
        float[] components = new float[] {
            color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f };
        PDColor pdColor = new PDColor(components, PDDeviceRGB.INSTANCE);
        setStrokingColor(pdColor);
    }

    /**
     * Set the color components of current stroking color space.
     *
     * @param components The components to set for the current color.
     * @throws IOException If there is an error while writing to the stream.
     * @deprecated Use {@link #setStrokingColor(PDColor)} instead.
     */
    @Deprecated
    public void setStrokingColor(float[] components) throws IOException
    {
        if (strokingColorSpaceStack.isEmpty())
        {
            throw new IllegalStateException("The color space must be set before setting a color");
        }

        for (float component : components)
        {
            writeOperand(component);
        }

        PDColorSpace currentStrokingColorSpace = strokingColorSpaceStack.peek();

//        if (currentStrokingColorSpace instanceof PDSeparation ||
//            currentStrokingColorSpace instanceof PDPattern ||
//            currentStrokingColorSpace instanceof PDICCBased)
//        {
//            writeOperator("SCN");
//        }
//        else
//        {
            writeOperator("SC");
//        } TODO: PdfBox-Android
    }

    /**
     * Set the stroking color in the DeviceRGB color space. Range is 0..255.
     *
     * @param r The red value
     * @param g The green value.
     * @param b The blue value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameters are invalid.
     */
    public void setStrokingColor(int r, int g, int b) throws IOException
    {
        if (isOutside255Interval(r) || isOutside255Interval(g) || isOutside255Interval(b))
        {
            throw new IllegalArgumentException("Parameters must be within 0..255, but are "
                + String.format("(%d,%d,%d)", r, g, b));
        }
        writeOperand(r / 255f);
        writeOperand(g / 255f);
        writeOperand(b / 255f);
        writeOperator("RG");
        setStrokingColorSpaceStack(PDDeviceRGB.INSTANCE);
    }

    /**
     * Set the stroking color in the DeviceCMYK color space. Range is 0..255.
     *
     * @param c The cyan value.
     * @param m The magenta value.
     * @param y The yellow value.
     * @param k The black value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameters are invalid.
     * @deprecated Use {@link #setStrokingColor(float, float, float, float)} instead.
     */
    @Deprecated
    public void setStrokingColor(int c, int m, int y, int k) throws IOException
    {
        if (isOutside255Interval(c) || isOutside255Interval(m) || isOutside255Interval(y) || isOutside255Interval(k))
        {
            throw new IllegalArgumentException("Parameters must be within 0..255, but are "
                + String.format("(%d,%d,%d,%d)", c, m, y, k));
        }
        setStrokingColor(c / 255f, m / 255f, y / 255f, k / 255f);
    }

    /**
     * Set the stroking color in the DeviceCMYK color space. Range is 0..1
     *
     * @param c The cyan value.
     * @param m The magenta value.
     * @param y The yellow value.
     * @param k The black value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameters are invalid.
     */
    public void setStrokingColor(float c, float m, float y, float k) throws IOException
    {
        if (isOutsideOneInterval(c) || isOutsideOneInterval(m) || isOutsideOneInterval(y) || isOutsideOneInterval(k))
        {
            throw new IllegalArgumentException("Parameters must be within 0..1, but are "
                + String.format("(%.2f,%.2f,%.2f,%.2f)", c, m, y, k));
        }
        writeOperand(c);
        writeOperand(m);
        writeOperand(y);
        writeOperand(k);
        writeOperator("K");
//        setStrokingColorSpaceStack(PDDeviceCMYK.INSTANCE); TODO: PdfBox-Android
    }

    /**
     * Set the stroking color in the DeviceGray color space. Range is 0..255.
     *
     * @param g The gray value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameter is invalid.
     * @deprecated Use {@link #setStrokingColor(float)} instead.
     */
    @Deprecated
    public void setStrokingColor(int g) throws IOException
    {
        if (isOutside255Interval(g))
        {
            throw new IllegalArgumentException("Parameter must be within 0..255, but is " + g);
        }
        setStrokingColor(g / 255f);
    }

    /**
     * Set the stroking color in the DeviceGray color space. Range is 0..1.
     *
     * @param g The gray value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameter is invalid.
     * @deprecated use {@link #setStrokingColor(float) setStrokingColor(float)}
     */
    @Deprecated
    public void setStrokingColor(double g) throws IOException
    {
        setStrokingColor((float) g);
    }

    /**
     * Set the stroking color in the DeviceGray color space. Range is 0..1.
     *
     * @param g The gray value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameter is invalid.
     */
    public void setStrokingColor(float g) throws IOException
    {
        if (isOutsideOneInterval(g))
        {
            throw new IllegalArgumentException("Parameter must be within 0..1, but is " + g);
        }
        writeOperand(g);
        writeOperator("G");
        setStrokingColorSpaceStack(PDDeviceGray.INSTANCE);
    }

    /**
     * Sets the non-stroking color and, if necessary, the non-stroking color space.
     *
     * @param color Color in a specific color space.
     * @throws IOException If an IO error occurs while writing to the stream.
     */
    public void setNonStrokingColor(PDColor color) throws IOException
    {
        if (nonStrokingColorSpaceStack.isEmpty() ||
            nonStrokingColorSpaceStack.peek() != color.getColorSpace())
        {
            writeOperand(getName(color.getColorSpace()));
            writeOperator("cs");
            setNonStrokingColorSpaceStack(color.getColorSpace());
        }

        for (float value : color.getComponents())
        {
            writeOperand(value);
        }

//        if (color.getColorSpace() instanceof PDPattern)
//        {
//            writeOperand(color.getPatternName());
//        }

//        if (color.getColorSpace() instanceof PDPattern ||
//            color.getColorSpace() instanceof PDSeparation ||
//            color.getColorSpace() instanceof PDDeviceN ||
//            color.getColorSpace() instanceof PDICCBased)
//        {
//            writeOperator("scn");
//        }
//        else
//        {
            writeOperator("sc");
//        } TODO: PdfBox-Android
    }

    /**
     * Set the non-stroking color using an AWT color. Conversion uses the default sRGB color space.
     *
     * @param color The color to set.
     * @throws IOException If an IO error occurs while writing to the stream.
     */
    public void setNonStrokingColor(AWTColor color) throws IOException
    {
        float[] components = new float[] {
            color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f };
        PDColor pdColor = new PDColor(components, PDDeviceRGB.INSTANCE);
        setNonStrokingColor(pdColor);
    }

    /**
     * Set the color components of current non-stroking color space.
     *
     * @param components The components to set for the current color.
     * @throws IOException If there is an error while writing to the stream.
     * @deprecated Use {@link #setNonStrokingColor(PDColor)} instead.
     */
    @Deprecated
    public void setNonStrokingColor(float[] components) throws IOException
    {
        if (nonStrokingColorSpaceStack.isEmpty())
        {
            throw new IllegalStateException("The color space must be set before setting a color");
        }

        for (float component : components)
        {
            writeOperand(component);
        }

        PDColorSpace currentNonStrokingColorSpace = nonStrokingColorSpaceStack.peek();

//        if (currentNonStrokingColorSpace instanceof PDSeparation ||
//            currentNonStrokingColorSpace instanceof PDPattern ||
//            currentNonStrokingColorSpace instanceof PDICCBased)
//        {
//            writeOperator("scn");
//        }
//        else
//        {
            writeOperator("sc");
//        } TODO: PdfBox-Android
    }

    /**
     * Set the non-stroking color in the DeviceRGB color space. Range is 0..255.
     *
     * @param r The red value.
     * @param g The green value.
     * @param b The blue value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameters are invalid.
     */
    public void setNonStrokingColor(int r, int g, int b) throws IOException
    {
        if (isOutside255Interval(r) || isOutside255Interval(g) || isOutside255Interval(b))
        {
            throw new IllegalArgumentException("Parameters must be within 0..255, but are "
                + String.format("(%d,%d,%d)", r, g, b));
        }
        writeOperand(r / 255f);
        writeOperand(g / 255f);
        writeOperand(b / 255f);
        writeOperator("rg");
        setNonStrokingColorSpaceStack(PDDeviceRGB.INSTANCE);
    }

    /**
     * Set the non-stroking color in the DeviceCMYK color space. Range is 0..255.
     *
     * @param c The cyan value.
     * @param m The magenta value.
     * @param y The yellow value.
     * @param k The black value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameters are invalid.
     */
    public void setNonStrokingColor(int c, int m, int y, int k) throws IOException
    {
        if (isOutside255Interval(c) || isOutside255Interval(m) || isOutside255Interval(y) || isOutside255Interval(k))
        {
            throw new IllegalArgumentException("Parameters must be within 0..255, but are "
                + String.format("(%d,%d,%d,%d)", c, m, y, k));
        }
        setNonStrokingColor(c / 255f, m / 255f, y / 255f, k / 255f);
    }

    /**
     * Set the non-stroking color in the DeviceCMYK color space. Range is 0..1.
     *
     * @param c The cyan value.
     * @param m The magenta value.
     * @param y The yellow value.
     * @param k The black value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @deprecated use
     * {@link #setNonStrokingColor(float, float, float, float) setNonStrokingColor(float, float, float, float)}
     */
    @Deprecated
    public void setNonStrokingColor(double c, double m, double y, double k) throws IOException
    {
        setNonStrokingColor((float) c, (float) m, (float) y, (float) k);
    }

    /**
     * Set the non-stroking color in the DeviceCMYK color space. Range is 0..1.
     *
     * @param c The cyan value.
     * @param m The magenta value.
     * @param y The yellow value.
     * @param k The black value.
     * @throws IOException If an IO error occurs while writing to the stream.
     */
    public void setNonStrokingColor(float c, float m, float y, float k) throws IOException
    {
        if (isOutsideOneInterval(c) || isOutsideOneInterval(m) || isOutsideOneInterval(y) || isOutsideOneInterval(k))
        {
            throw new IllegalArgumentException("Parameters must be within 0..1, but are "
                + String.format("(%.2f,%.2f,%.2f,%.2f)", c, m, y, k));
        }
        writeOperand(c);
        writeOperand(m);
        writeOperand(y);
        writeOperand(k);
        writeOperator("k");
//        setNonStrokingColorSpaceStack(PDDeviceCMYK.INSTANCE); TODO: PdfBox-Android
    }

    /**
     * Set the non-stroking color in the DeviceGray color space. Range is 0..255.
     *
     * @param g The gray value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameter is invalid.
     */
    public void setNonStrokingColor(int g) throws IOException
    {
        if (isOutside255Interval(g))
        {
            throw new IllegalArgumentException("Parameter must be within 0..255, but is " + g);
        }
        setNonStrokingColor(g / 255f);
    }

    /**
     * Set the non-stroking color in the DeviceGray color space. Range is 0..1.
     *
     * @param g The gray value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameter is invalid.
     * @deprecated use {@link #setNonStrokingColor(float) setNonStrokingColor(float)}
     */
    @Deprecated
    public void setNonStrokingColor(double g) throws IOException
    {
        setNonStrokingColor((float) g);
    }

    /**
     * Set the non-stroking color in the DeviceGray color space. Range is 0..1.
     *
     * @param g The gray value.
     * @throws IOException If an IO error occurs while writing to the stream.
     * @throws IllegalArgumentException If the parameter is invalid.
     */
    public void setNonStrokingColor(float g) throws IOException
    {
        if (isOutsideOneInterval(g))
        {
            throw new IllegalArgumentException("Parameter must be within 0..1, but is " + g);
        }
        writeOperand(g);
        writeOperator("g");
        setNonStrokingColorSpaceStack(PDDeviceGray.INSTANCE);
    }

    /**
     * Add a rectangle to the current path.
     *
     * @param x The lower left x coordinate.
     * @param y The lower left y coordinate.
     * @param width The width of the rectangle.
     * @param height The height of the rectangle.
     * @throws IOException If the content stream could not be written.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void addRect(float x, float y, float width, float height) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: addRect is not allowed within a text block.");
        }
        writeOperand(x);
        writeOperand(y);
        writeOperand(width);
        writeOperand(height);
        writeOperator("re");
    }

    /**
     * Fill a rectangle on the page using the current non stroking color.
     *
     * @param x The lower left x coordinate.
     * @param y The lower left y coordinate.
     * @param width The width of the rectangle.
     * @param height The height of the rectangle.
     * @throws IOException If there is an error while drawing on the screen.
     * @throws IllegalStateException If the method was called within a text block.
     * @deprecated Use {@link #addRect} followed by {@link #fill()} instead.
     */
    @Deprecated
    public void fillRect(float x, float y, float width, float height) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: fillRect is not allowed within a text block.");
        }
        addRect(x, y, width, height);
        fill();
    }

    /**
     * Append a cubic Bézier curve to the current path. The curve extends from the current
     * point to the point (x3 , y3 ), using (x1 , y1 ) and (x2 , y2 ) as the Bézier control points
     * @param x1 x coordinate of the point 1
     * @param y1 y coordinate of the point 1
     * @param x2 x coordinate of the point 2
     * @param y2 y coordinate of the point 2
     * @param x3 x coordinate of the point 3
     * @param y3 y coordinate of the point 3
     * @throws IOException If there is an error while adding the .
     * @deprecated Use {@link #curveTo} instead.
     */
    @Deprecated
    public void addBezier312(float x1, float y1, float x2, float y2, float x3, float y3) throws IOException
    {
        curveTo(x1, y1, x2, y2, x3, y3);
    }

    /**
     * Append a cubic Bézier curve to the current path. The curve extends from the current point to
     * the point (x3, y3), using (x1, y1) and (x2, y2) as the Bézier control points.
     *
     * @param x1 x coordinate of the point 1
     * @param y1 y coordinate of the point 1
     * @param x2 x coordinate of the point 2
     * @param y2 y coordinate of the point 2
     * @param x3 x coordinate of the point 3
     * @param y3 y coordinate of the point 3
     * @throws IOException If the content stream could not be written.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: curveTo is not allowed within a text block.");
        }
        writeOperand(x1);
        writeOperand(y1);
        writeOperand(x2);
        writeOperand(y2);
        writeOperand(x3);
        writeOperand(y3);
        writeOperator("c");
    }

    /**
     * Append a cubic Bézier curve to the current path. The curve extends from the current
     * point to the point (x3 , y3 ), using the current point and (x2 , y2 ) as the Bézier control points/
     *
     * @param x2 x coordinate of the point 2
     * @param y2 y coordinate of the point 2
     * @param x3 x coordinate of the point 3
     * @param y3 y coordinate of the point 3
     * @throws IOException If there is an error while adding the .
     * @deprecated Use {@link #curveTo2} instead.
     */
    @Deprecated
    public void addBezier32(float x2, float y2, float x3, float y3) throws IOException
    {
        curveTo2(x2, y2, x3, y3);
    }

    /**
     * Append a cubic Bézier curve to the current path. The curve extends from the current point to
     * the point (x3, y3), using the current point and (x2, y2) as the Bézier control points.
     *
     * @param x2 x coordinate of the point 2
     * @param y2 y coordinate of the point 2
     * @param x3 x coordinate of the point 3
     * @param y3 y coordinate of the point 3
     * @throws IllegalStateException If the method was called within a text block.
     * @throws IOException If the content stream could not be written.
     */
    public void curveTo2(float x2, float y2, float x3, float y3) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: curveTo2 is not allowed within a text block.");
        }
        writeOperand(x2);
        writeOperand(y2);
        writeOperand(x3);
        writeOperand(y3);
        writeOperator("v");
    }

    /**
     * Append a cubic Bézier curve to the current path. The curve extends from the current
     * point to the point (x3 , y3 ), using (x1 , y1 ) and (x3 , y3 ) as the Bézier control points/
     *
     * @param x1 x coordinate of the point 1
     * @param y1 y coordinate of the point 1
     * @param x3 x coordinate of the point 3
     * @param y3 y coordinate of the point 3
     * @throws IOException If there is an error while adding the .
     * @deprecated Use {@link #curveTo1} instead.
     */
    @Deprecated
    public void addBezier31(float x1, float y1, float x3, float y3) throws IOException
    {
        curveTo1(x1, y1, x3, y3);
    }

    /**
     * Append a cubic Bézier curve to the current path. The curve extends from the current point to
     * the point (x3, y3), using (x1, y1) and (x3, y3) as the Bézier control points.
     *
     * @param x1 x coordinate of the point 1
     * @param y1 y coordinate of the point 1
     * @param x3 x coordinate of the point 3
     * @param y3 y coordinate of the point 3
     * @throws IOException If the content stream could not be written.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void curveTo1(float x1, float y1, float x3, float y3) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: curveTo1 is not allowed within a text block.");
        }
        writeOperand(x1);
        writeOperand(y1);
        writeOperand(x3);
        writeOperand(y3);
        writeOperator("y");
    }

    /**
     * Move the current position to the given coordinates.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @throws IOException If the content stream could not be written.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void moveTo(float x, float y) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: moveTo is not allowed within a text block.");
        }
        writeOperand(x);
        writeOperand(y);
        writeOperator("m");
    }

    /**
     * Draw a line from the current position to the given coordinates.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @throws IOException If the content stream could not be written.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void lineTo(float x, float y) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: lineTo is not allowed within a text block.");
        }
        writeOperand(x);
        writeOperand(y);
        writeOperator("l");
    }

    /**
     * add a line to the current path.
     *
     * @param xStart The start x coordinate.
     * @param yStart The start y coordinate.
     * @param xEnd The end x coordinate.
     * @param yEnd The end y coordinate.
     * @throws IOException If there is an error while adding the line.
     * @throws IllegalStateException If the method was called within a text block.
     * @deprecated Use {@link #moveTo moveto(xStart,yStart)} followed by
     * {@link #lineTo lineTo(xEnd,yEnd)}.
     */
    @Deprecated
    public void addLine(float xStart, float yStart, float xEnd, float yEnd) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: addLine is not allowed within a text block.");
        }
        moveTo(xStart, yStart);
        lineTo(xEnd, yEnd);
    }

    /**
     * Draw a line on the page using the current stroking color and the current line width.
     *
     * @param xStart The start x coordinate.
     * @param yStart The start y coordinate.
     * @param xEnd The end x coordinate.
     * @param yEnd The end y coordinate.
     * @throws IOException If there is an error while drawing on the screen.
     * @throws IllegalStateException If the method was called within a text block.
     * @deprecated Use {@link #moveTo moveto(xStart,yStart)} followed by
     * {@link #lineTo lineTo(xEnd,yEnd)} followed by {@link #stroke stroke()}.
     */
    @Deprecated
    public void drawLine(float xStart, float yStart, float xEnd, float yEnd) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: drawLine is not allowed within a text block.");
        }
        moveTo(xStart, yStart);
        lineTo(xEnd, yEnd);
        stroke();
    }

    /**
     * Add a polygon to the current path.
     * @param x x coordinate of each points
     * @param y y coordinate of each points
     * @throws IOException If there is an error while drawing on the screen.
     * @throws IllegalStateException If the method was called within a text block.
     * @throws IllegalArgumentException If the two arrays have different lengths.
     * @deprecated Use {@link #moveTo} and {@link #lineTo} methods instead.
     */
    @Deprecated
    public void addPolygon(float[] x, float[] y) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: addPolygon is not allowed within a text block.");
        }
        if (x.length != y.length)
        {
            throw new IllegalArgumentException("Error: some points are missing coordinate");
        }
        for (int i = 0; i < x.length; i++)
        {
            if (i == 0)
            {
                moveTo(x[i], y[i]);
            }
            else
            {
                lineTo(x[i], y[i]);
            }
        }
        closeSubPath();
    }

    /**
     * Draw a polygon on the page using the current stroking color.
     * @param x x coordinate of each points
     * @param y y coordinate of each points
     * @throws IOException If there is an error while drawing on the screen.
     * @throws IllegalStateException If the method was called within a text block.
     * @deprecated Use {@link #moveTo} and {@link #lineTo} methods instead.
     */
    @Deprecated
    public void drawPolygon(float[] x, float[] y) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: drawPolygon is not allowed within a text block.");
        }
        addPolygon(x, y);
        stroke();
    }

    /**
     * Draw and fill a polygon on the page using the current stroking / non stroking colors.
     * @param x x coordinate of each points
     * @param y y coordinate of each points
     * @throws IOException If there is an error while drawing on the screen.
     * @throws IllegalStateException If the method was called within a text block.
     * @deprecated Use {@link #moveTo} and {@link #lineTo} methods instead.
     */
    @Deprecated
    public void fillPolygon(float[] x, float[] y) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: fillPolygon is not allowed within a text block.");
        }
        addPolygon(x, y);
        fill();
    }

    /**
     * Stroke the path.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void stroke() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: stroke is not allowed within a text block.");
        }
        writeOperator("S");
    }

    /**
     * Close and stroke the path.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void closeAndStroke() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: closeAndStroke is not allowed within a text block.");
        }
        writeOperator("s");
    }

    /**
     * Fill the path.
     *
     * @param windingRule the winding rule to be used for filling
     * @throws IOException If the content stream could not be written
     * @throws IllegalArgumentException If the parameter is not a valid winding rule.
     * @deprecated Use {@link #fill()} or {@link #fillEvenOdd} instead.
     */
    @Deprecated
    public void fill(Path.FillType windingRule) throws IOException
    {
        if (windingRule == Path.FillType.WINDING)
        {
            fill();
        }
        else if (windingRule == Path.FillType.EVEN_ODD)
        {
            fillEvenOdd();
        }
        else
        {
            throw new IllegalArgumentException("Error: unknown value for winding rule");
        }
    }

    /**
     * Fills the path using the nonzero winding number rule.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void fill() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: fill is not allowed within a text block.");
        }
        writeOperator("f");
    }

    /**
     * Fills the path using the even-odd winding rule.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void fillEvenOdd() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: fillEvenOdd is not allowed within a text block.");
        }
        writeOperator("f*");
    }

    /**
     * Fill and then stroke the path, using the nonzero winding number rule to determine the region
     * to fill. This shall produce the same result as constructing two identical path objects,
     * painting the first with {@link #fill() } and the second with {@link #stroke() }.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void fillAndStroke() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: fillAndStroke is not allowed within a text block.");
        }
        writeOperator("B");
    }

    /**
     * Fill and then stroke the path, using the even-odd rule to determine the region to
     * fill. This shall produce the same result as constructing two identical path objects, painting
     * the first with {@link #fillEvenOdd() } and the second with {@link #stroke() }.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void fillAndStrokeEvenOdd() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: fillAndStrokeEvenOdd is not allowed within a text block.");
        }
        writeOperator("B*");
    }

    /**
     * Close, fill, and then stroke the path, using the nonzero winding number rule to determine the
     * region to fill. This shall have the same effect as the sequence {@link #closePath() }
     * and then {@link #fillAndStroke() }.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void closeAndFillAndStroke() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: closeAndFillAndStroke is not allowed within a text block.");
        }
        writeOperator("b");
    }

    /**
     * Close, fill, and then stroke the path, using the even-odd rule to determine the region to
     * fill. This shall have the same effect as the sequence {@link #closePath() }
     * and then {@link #fillAndStrokeEvenOdd() }.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void closeAndFillAndStrokeEvenOdd() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: closeAndFillAndStrokeEvenOdd is not allowed within a text block.");
        }
        writeOperator("b*");
    }

    /**
     * Fills the clipping area with the given shading.
     *
     * @param shading Shading resource
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void shadingFill(PDShading shading) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: shadingFill is not allowed within a text block.");
        }

        writeOperand(resources.add(shading));
        writeOperator("sh");
    }

    /**
     * Closes the current subpath.
     *
     * @throws IOException If the content stream could not be written
     * @deprecated Use {@link #closePath()} instead.
     */
    @Deprecated
    public void closeSubPath() throws IOException
    {
        closePath();
    }

    /**
     * Closes the current subpath.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void closePath() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: closePath is not allowed within a text block.");
        }
        writeOperator("h");
    }

    /**
     * Clip path.
     *
     * @param windingRule the winding rule to be used for clipping
     * @throws IOException If there is an error while clipping the path.
     * @throws IllegalStateException If the method was called within a text block.
     * @deprecated Use {@link #clip()} or {@link #clipEvenOdd} instead.
     */
    @Deprecated
    public void clipPath(Path.FillType windingRule) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: clipPath is not allowed within a text block.");
        }
        if (windingRule == Path.FillType.WINDING)
        {
            writeOperator("W");
        }
        else if (windingRule == Path.FillType.EVEN_ODD)
        {
            writeOperator("W*");
        }
        else
        {
            throw new IllegalArgumentException("Error: unknown value for winding rule");
        }
        writeOperator("n");
    }

    /**
     * Intersects the current clipping path with the current path, using the nonzero rule.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void clip() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: clip is not allowed within a text block.");
        }
        writeOperator("W");

        // end path without filling or stroking
        writeOperator("n");
    }

    /**
     * Intersects the current clipping path with the current path, using the even-odd rule.
     *
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void clipEvenOdd() throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: clipEvenOdd is not allowed within a text block.");
        }
        writeOperator("W*");

        // end path without filling or stroking
        writeOperator("n");
    }

    /**
     * Set line width to the given value.
     *
     * @param lineWidth The width which is used for drawing.
     * @throws IOException If the content stream could not be written
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void setLineWidth(float lineWidth) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: setLineWidth is not allowed within a text block.");
        }
        writeOperand(lineWidth);
        writeOperator("w");
    }

    /**
     * Set the line join style.
     *
     * @param lineJoinStyle 0 for miter join, 1 for round join, and 2 for bevel join.
     * @throws IOException If the content stream could not be written.
     * @throws IllegalStateException If the method was called within a text block.
     * @throws IllegalArgumentException If the parameter is not a valid line join style.
     */
    public void setLineJoinStyle(int lineJoinStyle) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: setLineJoinStyle is not allowed within a text block.");
        }
        if (lineJoinStyle >= 0 && lineJoinStyle <= 2)
        {
            writeOperand(lineJoinStyle);
            writeOperator("j");
        }
        else
        {
            throw new IllegalArgumentException("Error: unknown value for line join style");
        }
    }

    /**
     * Set the line cap style.
     *
     * @param lineCapStyle 0 for butt cap, 1 for round cap, and 2 for projecting square cap.
     * @throws IOException If the content stream could not be written.
     * @throws IllegalStateException If the method was called within a text block.
     * @throws IllegalArgumentException If the parameter is not a valid line cap style.
     */
    public void setLineCapStyle(int lineCapStyle) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: setLineCapStyle is not allowed within a text block.");
        }
        if (lineCapStyle >= 0 && lineCapStyle <= 2)
        {
            writeOperand(lineCapStyle);
            writeOperator("J");
        }
        else
        {
            throw new IllegalArgumentException("Error: unknown value for line cap style");
        }
    }

    /**
     * Set the line dash pattern.
     *
     * @param pattern The pattern array
     * @param phase The phase of the pattern
     * @throws IOException If the content stream could not be written.
     * @throws IllegalStateException If the method was called within a text block.
     */
    public void setLineDashPattern(float[] pattern, float phase) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: setLineDashPattern is not allowed within a text block.");
        }
        write("[");
        for (float value : pattern)
        {
            writeOperand(value);
        }
        write("] ");
        writeOperand(phase);
        writeOperator("d");
    }

    /**
     * Set the miter limit.
     *
     * @param miterLimit the new miter limit.
     * @throws IOException If the content stream could not be written.
     */
    public void setMiterLimit(float miterLimit) throws IOException
    {
        if (inTextMode)
        {
            throw new IllegalStateException("Error: setMiterLimit is not allowed within a text block.");
        }
        if (miterLimit <= 0.0)
        {
            throw new IllegalArgumentException("A miter limit <= 0 is invalid and will not render in Acrobat Reader");
        }
        writeOperand(miterLimit);
        writeOperator("M");
    }

    /**
     * Begin a marked content sequence.
     *
     * @param tag the tag
     * @throws IOException if an I/O error occurs
     * @deprecated Use {@link #beginMarkedContent} instead.
     */
    @Deprecated
    public void beginMarkedContentSequence(COSName tag) throws IOException
    {
        beginMarkedContent(tag);
    }

    /**
     * Begin a marked content sequence.
     *
     * @param tag the tag
     * @throws IOException If the content stream could not be written
     */
    public void beginMarkedContent(COSName tag) throws IOException
    {
        writeOperand(tag);
        writeOperator("BMC");
    }

    /**
     * Begin a marked content sequence with a reference to an entry in the page resources'
     * Properties dictionary.
     *
     * @param tag the tag
     * @param propsName the properties reference
     * @throws IOException if an I/O error occurs
     * @deprecated Use {@link #beginMarkedContent(COSName, PDPropertyList)} instead.
     */
    @Deprecated
    public void beginMarkedContentSequence(COSName tag, COSName propsName) throws IOException
    {
        writeOperand(tag);
        writeOperand(propsName);
        writeOperator("BDC");
    }

    /**
     * Begin a marked content sequence with a reference to an entry in the page resources'
     * Properties dictionary.
     *
     * @param tag the tag
     * @param propertyList property list
     * @throws IOException If the content stream could not be written
     */
    public void beginMarkedContent(COSName tag, PDPropertyList propertyList) throws IOException
    {
        writeOperand(tag);
        writeOperand(resources.add(propertyList));
        writeOperator("BDC");
    }

    /**
     * End a marked content sequence.
     *
     * @throws IOException If the content stream could not be written
     * @deprecated Use {@link #endMarkedContent} instead.
     */
    @Deprecated
    public void endMarkedContentSequence() throws IOException
    {
        endMarkedContent();
    }

    /**
     * End a marked content sequence.
     *
     * @throws IOException If the content stream could not be written
     */
    public void endMarkedContent() throws IOException
    {
        writeOperator("EMC");
    }

    /**
     * This will append raw commands to the content stream.
     *
     * @param commands The commands to append to the stream.
     * @throws IOException If an error occurs while writing to the stream.
     * @deprecated Usage of this method is discouraged.
     */
    @Deprecated
    public void appendRawCommands(String commands) throws IOException
    {
        output.write(commands.getBytes(Charsets.US_ASCII));
    }

    /**
     * This will append raw commands to the content stream.
     *
     * @param commands The commands to append to the stream.
     * @throws IOException If an error occurs while writing to the stream.
     * @deprecated Usage of this method is discouraged.
     */
    @Deprecated
    public void appendRawCommands(byte[] commands) throws IOException
    {
        output.write(commands);
    }

    /**
     * This will append raw commands to the content stream.
     *
     * @param data Append a raw byte to the stream.
     * @throws IOException If an error occurs while writing to the stream.
     * @deprecated Usage of this method is discouraged.
     */
    @Deprecated
    public void appendRawCommands(int data) throws IOException
    {
        output.write(data);
    }

    /**
     * This will append raw commands to the content stream.
     *
     * @param data Append a formatted double value to the stream.
     * @throws IOException If an error occurs while writing to the stream.
     * @deprecated Usage of this method is discouraged.
     */
    @Deprecated
    public void appendRawCommands(double data) throws IOException
    {
        output.write(formatDecimal.format(data).getBytes(Charsets.US_ASCII));
    }

    /**
     * This will append raw commands to the content stream.
     *
     * @param data Append a formatted float value to the stream.
     * @throws IOException If an error occurs while writing to the stream.
     * @deprecated Usage of this method is discouraged.
     */
    @Deprecated
    public void appendRawCommands(float data) throws IOException
    {
        output.write(formatDecimal.format(data).getBytes(Charsets.US_ASCII));
    }

    /**
     * This will append a {@link COSName} to the content stream.
     *
     * @param name the name
     * @throws IOException If an error occurs while writing to the stream.
     * @deprecated Usage of this method is discouraged.
     */
    @Deprecated
    public void appendCOSName(COSName name) throws IOException
    {
        name.writePDF(output);
    }

    /**
     * Set an extended graphics state.
     *
     * @param state The extended graphics state.
     * @throws IOException If the content stream could not be written.
     */
    public void setGraphicsStateParameters(PDExtendedGraphicsState state) throws IOException
    {
        writeOperand(resources.add(state));
        writeOperator("gs");
    }

    /**
     * Write a comment line.
     *
     * @param comment the comment to be added to the content stream.
     *
     * @throws IOException If the content stream could not be written.
     * @throws IllegalArgumentException If the comment contains a newline. This is not allowed, because the next line
     * could be ordinary PDF content.
     */
    public void addComment(String comment) throws IOException
    {
        if (comment.indexOf('\n') >= 0 || comment.indexOf('\r') >= 0)
        {
            throw new IllegalArgumentException("comment should not include a newline");
        }
        output.write('%');
        output.write(comment.getBytes(Charsets.US_ASCII));
        output.write('\n');
    }

    /**
     * Writes a real number to the content stream.
     *
     * @param real the float value to be added to the content stream.
     *
     * @throws IOException if something went wrong
     */
    protected void writeOperand(float real) throws IOException
    {
        int byteCount = NumberFormatUtil.formatFloatFast(real, formatDecimal.getMaximumFractionDigits(), formatBuffer);

        if (byteCount == -1)
        {
            //Fast formatting failed
            write(formatDecimal.format(real));
        }
        else
        {
            output.write(formatBuffer, 0, byteCount);
        }
        output.write(' ');
    }

    /**
     * Writes an integer number to the content stream.
     */
    private void writeOperand(int integer) throws IOException
    {
        write(formatDecimal.format(integer));
        output.write(' ');
    }

    /**
     * Writes a COSName to the content stream.
     */
    private void writeOperand(COSName name) throws IOException
    {
        name.writePDF(output);
        output.write(' ');
    }

    /**
     * Writes a string to the content stream as ASCII.
     */
    private void writeOperator(String text) throws IOException
    {
        output.write(text.getBytes(Charsets.US_ASCII));
        output.write('\n');
    }

    /**
     * Writes a string to the content stream as ASCII.
     */
    private void write(String text) throws IOException
    {
        output.write(text.getBytes(Charsets.US_ASCII));
    }

    /**
     * Writes a newline to the content stream as ASCII.
     */
    private void writeLine() throws IOException
    {
        output.write('\n');
    }

    /**
     * Writes binary data to the content stream.
     */
    private void writeBytes(byte[] data) throws IOException
    {
        output.write(data);
    }

    /**
     * Writes an AffineTransform to the content stream as an array.
     */
    private void writeAffineTransform(AffineTransform transform) throws IOException
    {
        double[] values = new double[6];
        transform.getMatrix(values);
        for (double v : values)
        {
            writeOperand((float) v);
        }
    }

    /**
     * Close the content stream.  This must be called when you are done with this object.
     *
     * @throws IOException If the underlying stream has a problem being written to.
     */
    @Override
    public void close() throws IOException
    {
        if (inTextMode)
        {
            Log.w("PdfBox-Android", "You did not call endText(), some viewers won't display your text");
        }
        if (output != null)
        {
            output.close();
            output = null;
        }
    }

    private boolean isOutside255Interval(int val)
    {
        return val < 0 || val > 255;
    }

    private boolean isOutsideOneInterval(double val)
    {
        return val < 0 || val > 1;
    }

    private void setStrokingColorSpaceStack(PDColorSpace colorSpace)
    {
        if (strokingColorSpaceStack.isEmpty())
        {
            strokingColorSpaceStack.add(colorSpace);
        }
        else
        {
            strokingColorSpaceStack.setElementAt(colorSpace, strokingColorSpaceStack.size() - 1);
        }
    }

    private void setNonStrokingColorSpaceStack(PDColorSpace colorSpace)
    {
        if (nonStrokingColorSpaceStack.isEmpty())
        {
            nonStrokingColorSpaceStack.add(colorSpace);
        }
        else
        {
            nonStrokingColorSpaceStack.setElementAt(colorSpace, nonStrokingColorSpaceStack.size() - 1);
        }
    }

    /**
     * Set the text rendering mode. This determines whether showing text shall cause glyph outlines
     * to be stroked, filled, used as a clipping boundary, or some combination of the three.
     *
     * @param rm The text rendering mode.
     * @throws IOException If the content stream could not be written.
     */
    public void setRenderingMode(RenderingMode rm) throws IOException
    {
        writeOperand(rm.intValue());
        writeOperator("Tr");
    }

    /**
     * Set the character spacing. The value shall be added to the horizontal or vertical component
     * of the glyph's displacement, depending on the writing mode.
     *
     * @param spacing character spacing
     * @throws IOException If the content stream could not be written.
     */
    public void setCharacterSpacing(float spacing) throws IOException
    {
        writeOperand(spacing);
        writeOperator("Tc");
    }

    /**
     * Set the word spacing. The value shall be added to the horizontal or vertical component of the
     * ASCII SPACE character, depending on the writing mode.
     * <p>
     * This will have an effect only with Type1 and TrueType fonts, not with Type0 fonts. The PDF
     * specification tells why: "Word spacing shall be applied to every occurrence of the
     * single-byte character code 32 in a string when using a simple font or a composite font that
     * defines code 32 as a single-byte code. It shall not apply to occurrences of the byte value 32
     * in multiple-byte codes."
     *
     * @param spacing word spacing
     * @throws IOException If the content stream could not be written.
     */
    public void setWordSpacing(float spacing) throws IOException
    {
        writeOperand(spacing);
        writeOperator("Tw");
    }

    /**
     * Set the horizontal scaling to scale / 100.
     *
     * @param scale number specifying the percentage of the normal width. Default value: 100 (normal
     * width).
     * @throws IOException If the content stream could not be written.
     */
    public void setHorizontalScaling(float scale) throws IOException
    {
        writeOperand(scale);
        writeOperator("Tz");
    }

    /**
     * Set the text rise value, i.e. move the baseline up or down. This is useful for drawing
     * superscripts or subscripts.
     *
     * @param rise Specifies the distance, in unscaled text space units, to move the baseline up or
     * down from its default location. 0 restores the default location.
     * @throws IOException
     */
    public void setTextRise(float rise) throws IOException
    {
        writeOperand(rise);
        writeOperator("Ts");
    }
}
