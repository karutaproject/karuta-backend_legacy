<?php

class DomUtils
{

static function get_inner_html( $node ) {
    $innerHTML= '';
    $children = $node->childNodes;
    foreach ($children as $child) {
        $innerHTML .= $child->ownerDocument->saveXML( $child );
    }

    return $innerHTML;
} 



static function getFieldFrom
$xmlProfile = $portfolioClient->getNode("d7e754d9-0da6-4b3e-84cf-a8588532b3e8");

		
		$doc = new DOMDocument();		
		$doc->loadXML($xmlProfile);
		
		
		$asmContexts = $doc->getElementsByTagName("asmContext");
		
		
		  foreach($asmContexts as $asmContext)
		  {
			//print_r($asmContext);
			echo $asmContext->getAttribute("id")."<br>";
			$metadata = $asmContext->getElementsByTagName("metadata")->item(0);
			
			if ($metadata->hasAttribute("semantictag")) 
			{
				
				if($metadata->getAttribute("semantictag")=="lastname")
				{
					
					$asmResources = $asmContext->getElementsByTagName("asmResource");
					foreach($asmResources as $asmResource)
					{
						
						if ($asmResource->hasAttribute("xsi_type")) 
						{
							if($asmResource->getAttribute("xsi_type")=="Field")
							{
								echo get_inner_html($asmResource->getElementsByTagName("text")->item(0)); 
							}
						}
					}
				}

				
			}
		  }
	
}

?>