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

package com.tom_roush.pdfbox.cos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.tom_roush.pdfbox.filter.DecodeOptions;
import com.tom_roush.pdfbox.filter.DecodeResult;
import com.tom_roush.pdfbox.filter.Filter;
import com.tom_roush.pdfbox.io.RandomAccess;
import com.tom_roush.pdfbox.io.RandomAccessInputStream;
import com.tom_roush.pdfbox.io.RandomAccessOutputStream;
import com.tom_roush.pdfbox.io.ScratchFile;

/**
 * An InputStream which reads from an encoded COS stream.
 *
 * @author John Hewson
 */
public final class COSInputStream extends FilterInputStream
{
    /**
     * Creates a new COSInputStream from an encoded input stream.
     *
     * @param filters Filters to be applied.
     * @param parameters Filter parameters.
     * @param in Encoded input stream.
     * @param scratchFile Scratch file to use, or null.
     * @return Decoded stream.
     * @throws IOException If the stream could not be read.
     */
    static COSInputStream create(List<Filter> filters, COSDictionary parameters, InputStream in,
        ScratchFile scratchFile) throws IOException
    {
        return create(filters, parameters, in, scratchFile, DecodeOptions.DEFAULT);
    }

    static COSInputStream create(List<Filter> filters, COSDictionary parameters, InputStream in,
        ScratchFile scratchFile, DecodeOptions options) throws IOException
    {
        List<DecodeResult> results = new ArrayList<DecodeResult>();
        InputStream input = in;
        if (filters.isEmpty())
        {
            input = in;
        }
        else
        {
            // apply filters
            for (int i = 0; i < filters.size(); i++)
            {
                if (scratchFile != null)
                {
                    // scratch file
                    final RandomAccess buffer = scratchFile.createBuffer();
                    DecodeResult result = filters.get(i).decode(input, new RandomAccessOutputStream(buffer), parameters, i, options);
                    results.add(result);
                    input = new RandomAccessInputStream(buffer)
                    {
                        @Override
                        public void close() throws IOException
                        {
                            buffer.close();
                        }
                    };
                }
                else
                {
                    // in-memory
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    DecodeResult result = filters.get(i).decode(input, output, parameters, i, options);
                    results.add(result);
                    input = new ByteArrayInputStream(output.toByteArray());
                }
            }
        }
        return new COSInputStream(input, results);
    }

    private final List<DecodeResult> decodeResults;

    /**
     * Constructor.
     *
     * @param input decoded stream
     * @param decodeResults results of decoding
     */
    private COSInputStream(InputStream input, List<DecodeResult> decodeResults)
    {
        super(input);
        this.decodeResults = decodeResults;
    }

    /**
     * Returns the result of the last filter, for use by repair mechanisms.
     *
     * @return the result of the decoding.
     */
    public DecodeResult getDecodeResult()
    {
        if (decodeResults.isEmpty())
        {
            return DecodeResult.DEFAULT;
        }
        else
        {
            return decodeResults.get(decodeResults.size() - 1);
        }
    }
}
