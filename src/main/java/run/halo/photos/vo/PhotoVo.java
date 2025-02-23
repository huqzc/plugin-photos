package run.halo.photos.vo;

import lombok.Builder;
import lombok.Value;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.theme.finders.vo.ExtensionVoOperator;
import run.halo.photos.Photo;
import java.util.Map;

/**
 * @author LIlGG
 */
@Value
@Builder
public class PhotoVo implements ExtensionVoOperator {
    
    MetadataOperator metadata;
    
    Photo.PhotoSpec spec;
    
    public static PhotoVo from(Photo photo) {
        return PhotoVo.builder()
            .metadata(photo.getMetadata())
            .spec(photo.getSpec())
            .build();
    }

    public static PhotoVo from(Attachment attachment, String groupName) {
        String url = attachment.getStatus().getPermalink();
        Photo.PhotoSpec sepc = new Photo.PhotoSpec();
        sepc.setDisplayName(attachment.getSpec().getDisplayName());
        sepc.setDescription(attachment.getSpec().getDisplayName());
        sepc.setUrl(url);
        sepc.setCover(url);
        sepc.setPriority(0);
        sepc.setGroupName(groupName);

        return PhotoVo.builder()
            .metadata(attachment.getMetadata())
            .spec(sepc)
            .build();
    }

    public static PhotoVo from(Attachment attachment, Map<String, String> map) {
        String url = attachment.getStatus().getPermalink();
        Photo.PhotoSpec sepc = new Photo.PhotoSpec();
        sepc.setDisplayName(attachment.getSpec().getDisplayName());
        sepc.setDescription(attachment.getSpec().getDisplayName());
        sepc.setUrl(url);
        sepc.setCover(url);
        sepc.setPriority(0);

        String attachmentGroup = attachment.getSpec().getGroupName();
        String photoGroup = map.get(attachmentGroup);
        sepc.setGroupName(photoGroup);

        return PhotoVo.builder()
            .metadata(attachment.getMetadata())
            .spec(sepc)
            .build();
    }
}
