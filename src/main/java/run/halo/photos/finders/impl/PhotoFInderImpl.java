package run.halo.photos.finders.impl;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.comparator.Comparators;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.finders.Finder;
import run.halo.photos.Photo;
import run.halo.photos.PhotoGroup;
import run.halo.photos.finders.PhotoFinder;
import run.halo.photos.vo.PhotoGroupVo;
import run.halo.photos.vo.PhotoVo;

/**
 * @author LIlGG
 */
@Finder("photoFinder")
public class PhotoFInderImpl implements PhotoFinder {
    private final ReactiveExtensionClient client;

    public PhotoFInderImpl(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Flux<PhotoVo> listAll() {
        return this.client.list(Photo.class, null, defaultPhotoComparator())
            .flatMap(photo -> Mono.just(PhotoVo.from(photo)));
    }

    @Override
    public Mono<ListResult<PhotoVo>> list(Integer page, Integer size) {
        return list(page, size, null);
    }

    @Override
    public Mono<ListResult<PhotoVo>> list(Integer page, Integer size, String group) {
        return pagePhoto(page, size, group, null, defaultPhotoComparator());
    }

    private Mono<ListResult<PhotoVo>> pagePhoto(Integer page, Integer size, String group,
        Predicate<Photo> photoPredicate, Comparator<Photo> comparator) {
        Predicate<Photo> predicate = photoPredicate == null ? photo -> true : photoPredicate;

        if (StringUtils.isEmpty(group)) {
            Mono<List<PhotoGroup>> groupListMono =
                client.list(PhotoGroup.class, null, defaultGroupComparator())
                    .filter(photoGroup -> !photoGroup.getSpec().isHidden())
                    .collectList();

            Integer finalPage = pageNullSafe(page);
            Integer finalSize = sizeNullSafe(size);
            return groupListMono.flatMap(groups -> {
                Map<String, String> attPhoMap = new HashMap<>();
                // 处理附件分组
                Set<String> attachmentGroups = groups.stream()
                    .filter(g -> StringUtils.isNotEmpty(g.getSpec().getAttachmentGroup()))
                    .map(g -> {
                        attPhoMap.put(g.getSpec().getAttachmentGroup(), g.getMetadata().getName());
                        System.out.println(
                            "map.put: " + g.getSpec().getAttachmentGroup() + "; " + g.getMetadata()
                                .getName());
                        return g.getSpec().getAttachmentGroup();
                    }).collect(Collectors.toSet());

                // 打印 map
                System.out.println("keys: " + attPhoMap.keySet());
                System.out.println("values: " + attPhoMap.values());

                // 处理普通图片分组
                Set<String> phoGroupNames = groups.stream()
                    .filter(g -> StringUtils.isEmpty(g.getSpec().getAttachmentGroup()))
                    .map(g -> g.getMetadata().getName()).collect(Collectors.toSet());

                // 查询附件数据
                Mono<ListResult<Attachment>> attachmentQuery = client.list(Attachment.class,
                    att -> attachmentGroups.contains(att.getSpec().getGroupName()), null,
                    finalPage, finalSize);

                // 构建新的图片查询条件
                Predicate<Photo> finalPredicate = predicate
                    .and(photo -> phoGroupNames.contains(photo.getSpec().getGroupName()));

                // 查询图片数据
                Mono<ListResult<Photo>> photoQuery =
                    client.list(Photo.class, finalPredicate, comparator,
                        finalPage,
                        finalSize);

                // 合并结果
                return Mono.zip(attachmentQuery, photoQuery)
                    .map(tuple -> {
                        ListResult<Attachment> attachmentResult = tuple.getT1();
                        ListResult<Photo> photoResult = tuple.getT2();

                        long total = attachmentResult.getTotal() + photoResult.getTotal();

                        List<PhotoVo> combinedPhotos = Stream.concat(
                            attachmentResult.get().map(a -> PhotoVo.from(a, attPhoMap)),
                            photoResult.get().map(PhotoVo::from)).collect(Collectors.toList());

                        return new ListResult<>(finalPage, finalSize, total, combinedPhotos);
                    });
            });
        } else {
            Integer finalPage = pageNullSafe(page);
            Integer finalSize = sizeNullSafe(size);
            // 处理指定分组的情况
            return client.fetch(PhotoGroup.class, group)
                .flatMap(photoGroup -> {
                    String attachmentGroup = photoGroup.getSpec().getAttachmentGroup();
                    if (StringUtils.isNotEmpty(attachmentGroup)) {
                        // 查询附件分组数据
                        Predicate<Attachment> attPredicate =
                            att -> StringUtils.equals(attachmentGroup,
                                att.getSpec().getGroupName());
                        return client.list(
                                Attachment.class,
                                attPredicate,
                                null,
                                finalPage,
                                finalSize)
                            .flatMap(list -> Flux.fromStream(list.get())
                                .concatMap(attachment -> Mono.just(PhotoVo.from(attachment, group)))
                                .collectList()
                                .map(photoVos -> new ListResult<>(finalPage, finalSize,
                                    list.getTotal(), photoVos)))
                            .defaultIfEmpty(new ListResult<>(finalPage, finalSize, 0L, List.of()));
                    } else {
                        // 构建新的图片查询条件
                        Predicate<Photo> finalPredicate = predicate
                            .and(photo -> StringUtils.equals(photoGroup.getMetadata().getName(),
                                photo.getSpec().getGroupName()));
                        // 查询普通图片分组数据
                        return client.list(
                                Photo.class,
                                finalPredicate,
                                comparator,
                                finalPage,
                                finalSize)
                            .flatMap(list -> Flux.fromStream(list.get())
                                .concatMap(photo -> Mono.just(PhotoVo.from(photo)))
                                .collectList()
                                .map(photoVos -> new ListResult<>(finalPage, finalSize,
                                    list.getTotal(), photoVos)))
                            .defaultIfEmpty(new ListResult<>(finalPage, finalSize, 0L, List.of()));
                    }
                })
                .switchIfEmpty(Mono.just(new ListResult<>(page, size, 0L, List.of())));
        }
    }

    @Override
    public Flux<PhotoVo> listBy(String groupName) {
        return client.list(Photo.class, photo -> {
            String group = photo.getSpec().getGroupName();
            return StringUtils.equals(group, groupName);
        }, defaultPhotoComparator()).flatMap(photo -> Mono.just(PhotoVo.from(photo)));
    }

    @Override
    public Flux<PhotoGroupVo> groupBy() {
        return this.client.list(PhotoGroup.class, null, defaultGroupComparator())
            .concatMap(group -> {
                PhotoGroupVo.PhotoGroupVoBuilder builder = PhotoGroupVo.from(group);
                return this.listBy(group.getMetadata().getName()).collectList().map(photos -> {
                    PhotoGroup.PostGroupStatus status = group.getStatus();
                    status.setPhotoCount(photos.size());
                    builder.status(status);
                    builder.photos(photos);
                    return builder.build();
                });
            });
    }

    static Comparator<PhotoGroup> defaultGroupComparator() {
        Function<PhotoGroup, Integer> priority = group -> group.getSpec().getPriority();
        Function<PhotoGroup, Instant> createTime =
            group -> group.getMetadata().getCreationTimestamp();
        Function<PhotoGroup, String> name = group -> group.getMetadata().getName();
        return Comparator.comparing(priority, Comparators.nullsLow()).thenComparing(createTime)
            .thenComparing(name);
    }

    static Comparator<Photo> defaultPhotoComparator() {
        Function<Photo, Integer> priority = link -> link.getSpec().getPriority();
        Function<Photo, Instant> createTime = link -> link.getMetadata().getCreationTimestamp();
        Function<Photo, String> name = link -> link.getMetadata().getName();
        return Comparator.comparing(priority, Comparators.nullsLow())
            .thenComparing(Comparator.comparing(createTime).reversed()).thenComparing(name);
    }

    int pageNullSafe(Integer page) {
        return ObjectUtils.defaultIfNull(page, 1);
    }

    int sizeNullSafe(Integer size) {
        return ObjectUtils.defaultIfNull(size, 10);
    }
}
