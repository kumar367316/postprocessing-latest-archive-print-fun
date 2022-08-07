package com.custom.postprocessing.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author kumar.charanswain
 *
 */

@Component
public class PostProcessUtil {
	
	@Value("${page.value}")
	private String pageValue;
	
	@Value("${numberic.symbol}")
	private String numbericSymbol;
	
	public String getFileType(String fileType) {
		if (fileType.matches(numbericSymbol)) {
			fileType = pageValue + fileType;
		}
		return fileType;
	}
}
