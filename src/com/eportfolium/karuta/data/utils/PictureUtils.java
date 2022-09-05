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

package com.eportfolium.karuta.data.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;


public class PictureUtils {

	public static void resizeImage(String entree, String sortie, String format) throws IOException {
	//======================================
		BufferedImage bufferedImage = ImageIO.read(new File(entree));

		int max = 480;
		if (bufferedImage.getHeight()>max || bufferedImage.getWidth()>max)
			bufferedImage = Scalr.resize(bufferedImage, max);

		File iwriter = new File(sortie);
		ImageIO.write(bufferedImage, format, iwriter);
	}

}
