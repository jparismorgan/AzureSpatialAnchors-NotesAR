// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
using System.Threading.Tasks;

namespace SharingService.Data
{
    /// <summary>
    /// An interface representing an anchor key cache.
    /// </summary>
    public interface IAnchorIdCache
    {
        /// <summary>
        /// Gets anchor ids asynchronously.
        /// </summary>
        /// <param name="groupingKey">Get anchor ids with this grouping key.</param>
        /// <returns>A list of anchor ids stored if available; otherwise, null.</returns>
        Task<string[]> GetAnchorIdsAsync(string groupingKey);

        /// <summary>
        /// Sets the grouping key and anchor id asynchronously.
        /// </summary>
        /// <param name="groupingKey">The grouping key between several anchors.</param>
        /// <param name="anchorId">The anchor id.</param>
        /// <returns>A Task</returns>
        Task SetAnchorIdAsync(string groupingKey, string anchorId);
    }
}
