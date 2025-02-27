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
package com.tom_roush.pdfbox.pdmodel.graphics.form;

import java.io.IOException;
import java.io.InputStream;

import com.tom_roush.harmony.awt.geom.AffineTransform;
import com.tom_roush.pdfbox.contentstream.PDContentStream;
import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSFloat;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSStream;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.ResourceCache;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.common.PDStream;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.util.Matrix;

/*
TODO There are further Form XObjects to implement:

+ PDFormXObject
|- PDReferenceXObject
|- PDGroupXObject
   |- PDTransparencyXObject

See PDF 32000 p111

When doing this all methods on PDFormXObject should probably be made
final and all fields private.
*/

/**
 * A Form XObject.
 *
 * @author Ben Litchfield
 */
public class PDFormXObject extends PDXObject implements PDContentStream
{
    private PDTransparencyGroupAttributes group;
    private final ResourceCache cache;

    /**
     * Creates a Form XObject for reading.
     * @param stream The XObject stream
     */
    public PDFormXObject(PDStream stream)
    {
        super(stream, COSName.FORM);
        cache = null;
    }

    /**
     * Creates a Form XObject for reading.
     * @param stream The XObject stream
     */
    public PDFormXObject(COSStream stream)
    {
        super(stream, COSName.FORM);
        cache = null;
    }

    /**
     * Creates a Form XObject for reading.
     * @param stream The XObject stream
     */
    public PDFormXObject(COSStream stream, ResourceCache cache)
    {
        super(stream, COSName.FORM);
        this.cache = cache;
    }

    /**
     * Creates a Form Image XObject for writing, in the given document.
     * @param document The current document
     */
    public PDFormXObject(PDDocument document)
    {
        super(document, COSName.FORM);
        cache = null;
    }

    /**
     * This will get the form type, currently 1 is the only form type.
     * @return The form type.
     */
    public int getFormType()
    {
        return getCOSObject().getInt(COSName.FORMTYPE, 1);
    }

    /**
     * Set the form type.
     * @param formType The new form type.
     */
    public void setFormType(int formType)
    {
        getCOSObject().setInt(COSName.FORMTYPE, formType);
    }

    /**
     * Returns the group attributes dictionary.
     *
     * @return the group attributes dictionary
     */
    public PDTransparencyGroupAttributes getGroup()
    {
        if( group == null )
        {
            COSDictionary dic = (COSDictionary) getCOSObject().getDictionaryObject(COSName.GROUP);
            if( dic != null )
            {
                group = new PDTransparencyGroupAttributes(dic);
            }
        }
        return group;
    }

    public PDStream getContentStream()
    {
        return new PDStream(getCOSObject());
    }

    @Override
    public InputStream getContents() throws IOException
    {
        return getCOSObject().createInputStream();
    }

    /**
     * This will get the resources for this Form XObject.
     * This will return null if no resources are available.
     *
     * @return The resources for this Form XObject.
     */
    @Override
    public PDResources getResources()
    {
        COSDictionary resources = getCOSObject().getCOSDictionary(COSName.RESOURCES);
        if (resources != null)
        {
            return new PDResources(resources, cache);
        }
        if (getCOSObject().containsKey(COSName.RESOURCES))
        {
            // PDFBOX-4372 if the resource key exists but has nothing, return empty resources,
            // to avoid a self-reference (xobject form Fm0 contains "/Fm0 Do")
            // See also the mention of PDFBOX-1359 in PDFStreamEngine
            return new PDResources();
        }
        return null;
    }

    /**
     * This will set the resources for this page.
     * @param resources The new resources for this page.
     */
    public void setResources(PDResources resources)
    {
        getCOSObject().setItem(COSName.RESOURCES, resources);
    }

    /**
     * An array of four numbers in the form coordinate system (see below),
     * giving the coordinates of the left, bottom, right, and top edges, respectively,
     * of the form XObject's bounding box.
     * These boundaries are used to clip the form XObject and to determine its size for caching.
     * @return The BBox of the form.
     */
    @Override
    public PDRectangle getBBox()
    {
        PDRectangle retval = null;
        COSArray array = (COSArray) getCOSObject().getDictionaryObject(COSName.BBOX);
        if (array != null)
        {
            retval = new PDRectangle(array);
        }
        return retval;
    }

    /**
     * This will set the BBox (bounding box) for this form.
     * @param bbox The new BBox for this form.
     */
    public void setBBox(PDRectangle bbox)
    {
        if (bbox == null)
        {
            getCOSObject().removeItem(COSName.BBOX);
        }
        else
        {
            getCOSObject().setItem(COSName.BBOX, bbox.getCOSArray());
        }
    }

    /**
     * This will get the optional matrix of an XObjectForm. It maps the form space to user space.
     * @return the form matrix if available, or the identity matrix.
     */
    @Override
    public Matrix getMatrix()
    {
        return Matrix.createMatrix(getCOSObject().getDictionaryObject(COSName.MATRIX));
    }

    /**
     * Sets the optional Matrix entry for the form XObject.
     * @param transform the transformation matrix
     */
    public void setMatrix(AffineTransform transform)
    {
        COSArray matrix = new COSArray();
        double[] values = new double[6];
        transform.getMatrix(values);
        for (double v : values)
        {
            matrix.add(new COSFloat((float) v));
        }
        getCOSObject().setItem(COSName.MATRIX, matrix);
    }

    /**
     * This will get the key of this XObjectForm in the structural parent tree.
     * Required if the form XObject contains marked-content sequences that are
     * structural content items.
     * @return the integer key of the XObjectForm's entry in the structural parent tree
     */
    public int getStructParents()
    {
        return getCOSObject().getInt(COSName.STRUCT_PARENTS, 0);
    }

    /**
     * This will set the key for this XObjectForm in the structural parent tree.
     * @param structParent The new key for this XObjectForm.
     */
    public void setStructParents(int structParent)
    {
        getCOSObject().setInt(COSName.STRUCT_PARENTS, structParent);
    }
}
