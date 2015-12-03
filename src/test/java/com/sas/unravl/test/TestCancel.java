// Copyright (c) 2015, SAS Institute Inc., Cary, NC, USA, All Rights Reserved
package com.sas.unravl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sas.unravl.UnRAVLException;
import com.sas.unravl.UnRAVLRuntime;

import java.io.IOException;

import org.junit.Test;

public class TestCancel extends TestBase {

    @Test
    public void test()
            throws UnRAVLException, JsonProcessingException, IOException {

        UnRAVLRuntime rt = new UnRAVLRuntime();

        assertFalse(rt.isCanceled());
        rt.execute(mockJson("{ 'env' : { 'shouldBeSet' : true }  }"),
                mockJson(
                        "{ 'bind' : { 'groovy' :  { 'canceledScript' : 'unravlScript.cancel(); true' }}}"),
                mockJson("{ 'env' : { 'shouldNotBeSet' : true }  }"));
        assertTrue(rt.isCanceled());
        assertEquals(Boolean.TRUE, rt.binding("shouldBeSet"));
        assertEquals(Boolean.TRUE, rt.binding("canceledScript"));
        assertFalse(rt.bound("shouldNotBeSet"));
    }
}
