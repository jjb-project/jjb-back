package project.jjb.member.domain;

public record OwnerProfile(
	String storeName,
	String storeAddress,
	String businessCategory,
	String storeIntroduction,
	String imageUrl
) {
}
