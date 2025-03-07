/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.scheduling.parameters;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import org.hisp.dhis.common.DxfNamespaces;
import org.hisp.dhis.feedback.ErrorReport;
import org.hisp.dhis.scheduling.JobParameters;
import org.hisp.dhis.scheduling.parameters.jackson.TrackerTrigramIndexJobParametersDeserializer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * @author Ameen
 */
@JacksonXmlRootElement( localName = "jobParameters", namespace = DxfNamespaces.DXF_2_0 )
@JsonDeserialize( using = TrackerTrigramIndexJobParametersDeserializer.class )
@AllArgsConstructor
@NoArgsConstructor
public class TrackerTrigramIndexJobParameters
    implements JobParameters
{
    private static final long serialVersionUID = 8705929982703731451L;

    private Set<String> attributes = new HashSet<>();

    private boolean skipIndexDeletion = false;

    private boolean runVacuum = false;

    private boolean runAnalyze = false;

    @JsonProperty
    @JacksonXmlElementWrapper( localName = "attributes", namespace = DxfNamespaces.DXF_2_0 )
    @JacksonXmlProperty( localName = "attributes", namespace = DxfNamespaces.DXF_2_0 )
    public Set<String> getAttributes()
    {
        return attributes;
    }

    public void setAttributes( Set<String> attributes )
    {
        this.attributes = attributes;
    }

    @JsonProperty
    @JacksonXmlElementWrapper( localName = "skipIndexDeletion", namespace = DxfNamespaces.DXF_2_0 )
    @JacksonXmlProperty( localName = "skipIndexDeletion", namespace = DxfNamespaces.DXF_2_0 )
    public boolean isSkipIndexDeletion()
    {
        return skipIndexDeletion;
    }

    public void setSkipIndexDeletion( boolean skipIndexDeletion )
    {
        this.skipIndexDeletion = skipIndexDeletion;
    }

    @JsonProperty
    @JacksonXmlProperty( namespace = DxfNamespaces.DXF_2_0 )
    public boolean isRunVacuum()
    {
        return runVacuum;
    }

    public void setRunVacuum( boolean runVacuum )
    {
        this.runVacuum = runVacuum;
    }

    @JsonProperty
    @JacksonXmlProperty( namespace = DxfNamespaces.DXF_2_0 )
    public boolean isRunAnalyze()
    {
        return runAnalyze;
    }

    public void setRunAnalyze( boolean runAnalyze )
    {
        this.runAnalyze = runAnalyze;
    }

    @Override
    public Optional<ErrorReport> validate()
    {
        return Optional.empty();
    }
}
